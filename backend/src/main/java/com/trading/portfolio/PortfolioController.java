package com.trading.portfolio;

import com.trading.broker.BrokerAdapter;
import com.trading.broker.BrokerAdapterFactory;
import com.trading.broker.BrokerTokenException;
import com.trading.portfolio.dto.LivePositionResponse;
import com.trading.portfolio.dto.OrderPreviewResponse;
import com.trading.portfolio.dto.OrderResponse;
import com.trading.portfolio.dto.PositionResponse;
import com.trading.signals.Order;
import com.trading.signals.OrderRepository;
import com.trading.signals.Position;
import com.trading.signals.PositionStatus;
import com.trading.users.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioDbService db;
    private final PortfolioEngine engine;
    private final BrokerAdapterFactory brokerAdapterFactory;
    private final OrderRepository orderRepository;

    /**
     * GET /api/portfolio/positions
     * Returns all positions for the authenticated user.
     * Optional query param: ?status=ACTIVE (filters by status)
     */
    @GetMapping("/positions")
    public List<PositionResponse> getPositions(
            Authentication auth,
            @RequestParam(required = false) PositionStatus status) {

        Long userId = resolveUserId(auth);
        List<Position> positions = (status == null)
                ? db.getAllPositionsForUser(userId)
                : db.getPositionsByStatus(userId, status);

        return positions.stream().map(PositionResponse::from).toList();
    }

    /**
     * POST /api/portfolio/positions/{id}/cancel
     * Cancels the entry order for a PENDING_ENTRY position owned by the caller.
     */
    @PostMapping("/positions/{id}/cancel")
    public ResponseEntity<PositionResponse> cancelPending(
            @PathVariable Long id,
            Authentication auth) {

        Long userId = resolveUserId(auth);

        List<Position> pending = db.getPositionsByStatus(userId, PositionStatus.PENDING_ENTRY);
        pending.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pending position not found or not owned by caller: " + id));

        engine.cancelPendingPosition(id);

        Position updated = db.getAllPositionsForUser(userId).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow();

        return ResponseEntity.ok(PositionResponse.from(updated));
    }

    /**
     * POST /api/portfolio/positions/{id}/exit
     * Triggers a manual exit for the given position (must be ACTIVE and owned by caller).
     */
    @PostMapping("/positions/{id}/exit")
    public ResponseEntity<PositionResponse> manualExit(
            @PathVariable Long id,
            Authentication auth) {

        Long userId = resolveUserId(auth);

        // Ownership check — only the owning user can exit their position
        List<Position> active = db.getActivePositions();
        Position pos = active.stream()
                .filter(p -> p.getId().equals(id) && p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active position not found or not owned by caller: " + id));

        engine.manualExit(id);

        // Return updated position
        Position updated = db.getAllPositionsForUser(userId).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow();

        return ResponseEntity.ok(PositionResponse.from(updated));
    }

    /**
     * GET /api/portfolio/positions/live
     * Returns ACTIVE positions enriched with live LTP and unrealised P&L.
     * Falls back gracefully (ltp = null) when Zerodha is not connected.
     */
    @GetMapping("/positions/live")
    public List<LivePositionResponse> getLivePositions(Authentication auth) {

        Long userId = resolveUserId(auth);
        List<Position> active = db.getPositionsByStatus(userId, PositionStatus.ACTIVE);

        if (active.isEmpty()) return List.of();

        // Try to fetch live quotes; fall back to null LTP if not connected
        Map<String, BigDecimal> quotes = fetchQuotes(userId, active);

        return active.stream()
                .map(pos -> LivePositionResponse.of(pos, quotes.get(pos.getSymbol())))
                .toList();
    }

    /**
     * GET /api/portfolio/orders
     * Returns orders for the authenticated user, sorted newest first, paginated.
     * Defaults: page=0, size=50.
     */
    @GetMapping("/orders")
    public List<OrderResponse> getOrders(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = resolveUserId(auth);
        return orderRepository.findByUserIdOrderByPlacedAtDesc(userId, PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * GET /api/portfolio/signals/{signalId}/order-preview
     * Returns an estimated order preview (qty, cost, blocking reason) without placing anything.
     */
    @GetMapping("/signals/{signalId}/order-preview")
    public ResponseEntity<OrderPreviewResponse> orderPreview(
            @PathVariable Long signalId,
            Authentication auth) {

        Long userId = resolveUserId(auth);
        UserConfig config = db.getUserConfigByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User config not found"));
        return ResponseEntity.ok(engine.previewOrderForSignal(config, signalId));
    }

    /**
     * POST /api/portfolio/signals/{signalId}/place-order
     * Immediately places a limit entry order for the given signal on behalf of the caller.
     */
    @PostMapping("/signals/{signalId}/place-order")
    public ResponseEntity<PositionResponse> placeOrder(
            @PathVariable Long signalId,
            Authentication auth) {

        Long userId = resolveUserId(auth);
        UserConfig config = db.getUserConfigByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User config not found"));

        Long positionId = engine.placeOrderForSignal(config, signalId);
        Position pos = db.getPositionById(positionId)
                .orElseThrow(() -> new IllegalStateException("Position not found after creation"));
        return ResponseEntity.ok(PositionResponse.from(pos));
    }

    private Map<String, BigDecimal> fetchQuotes(Long userId, List<Position> positions) {
        Optional<UserConfig> configOpt = db.getUserConfigByUserId(userId);
        if (configOpt.isEmpty()) return Map.of();
        try {
            BrokerAdapter adapter = brokerAdapterFactory.forUser(configOpt.get());
            List<String> symbols = positions.stream().map(Position::getSymbol).toList();
            return adapter.getQuotes(symbols);
        } catch (BrokerTokenException e) {
            log.debug("Live quotes unavailable for user {} (token/permission issue): {}", userId, e.getMessage());
            return Map.of();
        } catch (Exception e) {
            log.debug("Live quotes unavailable for user {} ({}): {}", userId, e.getClass().getSimpleName(), e.getMessage());
            return Map.of();
        }
    }

    private Long resolveUserId(Authentication auth) {
        return db.getUserIdByEmail(auth.getName());
    }
}
