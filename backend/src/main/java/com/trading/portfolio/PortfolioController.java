package com.trading.portfolio;

import com.trading.broker.BrokerAdapter;
import com.trading.portfolio.dto.AdjustQuantityRequest;
import com.trading.portfolio.dto.ConfirmFillRequest;
import com.trading.portfolio.dto.CreateManualOrderRequest;
import com.trading.portfolio.dto.LivePositionResponse;
import com.trading.portfolio.dto.OrderPreviewResponse;
import com.trading.portfolio.dto.OrderResponse;
import com.trading.portfolio.dto.PositionResponse;
import com.trading.signals.Order;
import com.trading.signals.OrderRepository;
import com.trading.signals.Position;
import com.trading.signals.PositionStatus;
import com.trading.users.UserConfig;
import jakarta.validation.Valid;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioDbService db;
    private final PortfolioEngine engine;
    private final LivePriceService livePriceService;
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
     * POST /api/portfolio/positions/{id}/confirm-fill
     * Manually confirms a fill for a PENDING_ENTRY position owned by the caller, whose order
     * can no longer be verified with Zerodha (e.g. order_id aged past the trading day). The
     * caller must have independently verified the actual fill on Zerodha first — this never
     * queries the broker to decide fill status.
     */
    @PostMapping("/positions/{id}/confirm-fill")
    public ResponseEntity<PositionResponse> confirmFill(
            @PathVariable Long id,
            @RequestBody @Valid ConfirmFillRequest req,
            Authentication auth) {

        Long userId = resolveUserId(auth);

        List<Position> pending = db.getPositionsByStatus(userId, PositionStatus.PENDING_ENTRY);
        pending.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pending position not found or not owned by caller: " + id));

        engine.confirmManualFill(id, req.quantity(), req.avgPrice());

        Position updated = db.getPositionById(id).orElseThrow();

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
     * POST /api/portfolio/positions/{id}/add-quantity
     * Places a live market buy order to add to an ACTIVE position owned by the caller.
     */
    @PostMapping("/positions/{id}/add-quantity")
    public ResponseEntity<PositionResponse> addQuantity(
            @PathVariable Long id,
            @RequestBody @Valid AdjustQuantityRequest req,
            Authentication auth) {

        Position pos = requireOwnedActivePosition(id, resolveUserId(auth));
        engine.addQuantity(pos.getId(), req.quantity());
        return ResponseEntity.ok(PositionResponse.from(db.getPositionById(id).orElseThrow()));
    }

    /**
     * POST /api/portfolio/positions/{id}/record-add-quantity
     * Records shares the caller already bought directly in Zerodha — no order is placed.
     */
    @PostMapping("/positions/{id}/record-add-quantity")
    public ResponseEntity<PositionResponse> recordAddQuantity(
            @PathVariable Long id,
            @RequestBody @Valid ConfirmFillRequest req,
            Authentication auth) {

        Position pos = requireOwnedActivePosition(id, resolveUserId(auth));
        engine.recordAddQuantity(pos.getId(), req.quantity(), req.avgPrice());
        return ResponseEntity.ok(PositionResponse.from(db.getPositionById(id).orElseThrow()));
    }

    /**
     * POST /api/portfolio/positions/{id}/remove-quantity
     * Places a live market sell order to trim an ACTIVE position owned by the caller. Rejects
     * removing the entire remaining quantity — use /exit to close the position instead.
     */
    @PostMapping("/positions/{id}/remove-quantity")
    public ResponseEntity<PositionResponse> removeQuantity(
            @PathVariable Long id,
            @RequestBody @Valid AdjustQuantityRequest req,
            Authentication auth) {

        Position pos = requireOwnedActivePosition(id, resolveUserId(auth));
        engine.removeQuantity(pos.getId(), req.quantity());
        return ResponseEntity.ok(PositionResponse.from(db.getPositionById(id).orElseThrow()));
    }

    /**
     * POST /api/portfolio/positions/{id}/record-remove-quantity
     * Records shares the caller already sold directly in Zerodha — no order is placed.
     */
    @PostMapping("/positions/{id}/record-remove-quantity")
    public ResponseEntity<PositionResponse> recordRemoveQuantity(
            @PathVariable Long id,
            @RequestBody @Valid ConfirmFillRequest req,
            Authentication auth) {

        Position pos = requireOwnedActivePosition(id, resolveUserId(auth));
        engine.recordRemoveQuantity(pos.getId(), req.quantity(), req.avgPrice());
        return ResponseEntity.ok(PositionResponse.from(db.getPositionById(id).orElseThrow()));
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

        // Try to fetch live prices; fall back to null LTP if not connected
        Map<String, BigDecimal> prices = fetchLivePrices(userId, active);

        return active.stream()
                .map(pos -> LivePositionResponse.of(pos, prices.get(pos.getSymbol())))
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

    /**
     * POST /api/portfolio/signals/{signalId}/manual-order
     * Records a trade the caller already placed and had filled directly in Zerodha (outside this app) —
     * no order is placed here. The caller asserts the actual fill quantity/price; this app only starts
     * tracking the position from that point on (GTT target, closing-basis SL monitoring, exits).
     */
    @PostMapping("/signals/{signalId}/manual-order")
    public ResponseEntity<PositionResponse> recordManualOrder(
            @PathVariable Long signalId,
            @RequestBody @Valid ConfirmFillRequest req,
            Authentication auth) {

        Long userId = resolveUserId(auth);
        UserConfig config = db.getUserConfigByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User config not found"));

        Long positionId = engine.recordManualOrder(config, signalId, req.quantity(), req.avgPrice());
        Position pos = db.getPositionById(positionId)
                .orElseThrow(() -> new IllegalStateException("Position not found after creation"));
        return ResponseEntity.ok(PositionResponse.from(pos));
    }

    /**
     * POST /api/portfolio/manual-orders
     * Same as {@link #recordManualOrder}, for a trade with no pre-existing tracked signal: creates a
     * manual signal and records the fill against it in one call.
     */
    @PostMapping("/manual-orders")
    public ResponseEntity<PositionResponse> recordManualOrderForNewSignal(
            @RequestBody @Valid CreateManualOrderRequest req,
            Authentication auth) {

        Long userId = resolveUserId(auth);
        UserConfig config = db.getUserConfigByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User config not found"));

        Long positionId = engine.recordManualOrderForNewSignal(config, req.signal(), req.quantity(), req.avgPrice());
        Position pos = db.getPositionById(positionId)
                .orElseThrow(() -> new IllegalStateException("Position not found after creation"));
        return ResponseEntity.ok(PositionResponse.from(pos));
    }

    /**
     * Delegates to {@link LivePriceService}, which sources last-traded prices from holdings +
     * day positions rather than {@link BrokerAdapter#getQuotes}, since the quote/market-data API
     * requires a subscription many API keys don't have, while holdings and day positions are part
     * of the base Portfolio API and return {@code last_price} regardless.
     */
    private Map<String, BigDecimal> fetchLivePrices(Long userId, List<Position> positions) {
        Optional<UserConfig> configOpt = db.getUserConfigByUserId(userId);
        if (configOpt.isEmpty()) return Map.of();

        Set<String> symbols = positions.stream().map(Position::getSymbol).collect(Collectors.toSet());
        return livePriceService.getLivePrices(configOpt.get(), symbols);
    }

    private Long resolveUserId(Authentication auth) {
        return db.getUserIdByEmail(auth.getName());
    }

    private Position requireOwnedActivePosition(Long id, Long userId) {
        return db.getActivePositions().stream()
                .filter(p -> p.getId().equals(id) && p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active position not found or not owned by caller: " + id));
    }
}
