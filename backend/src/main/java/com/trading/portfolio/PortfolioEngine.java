package com.trading.portfolio;

import com.trading.broker.*;
import com.trading.portfolio.events.*;
import com.trading.signals.*;
import com.trading.users.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates all portfolio lifecycle operations:
 * <ul>
 *   <li>{@link #runCoreLoop()} — places new entry orders</li>
 *   <li>{@link #checkOrderFills()} — detects fills / expirations</li>
 *   <li>{@link #reconcileGttExits()} — detects triggered GTT exits</li>
 *   <li>{@link #detectUnmanagedPositions()} — alerts on Zerodha holdings not tracked</li>
 *   <li>{@link #manualExit(Long)} — user-initiated market sell</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioEngine {

    private static final List<PositionStatus> ACTIVE_STATUSES =
            List.of(PositionStatus.PENDING_ENTRY, PositionStatus.ACTIVE);

    private final PortfolioDbService db;
    private final BrokerAdapterFactory brokerAdapterFactory;
    private final SignalScoringService scoringService;
    private final PositionSizingService sizingService;
    private final ApplicationEventPublisher events;

    // ── Core loop ────────────────────────────────────────────────────────────

    public void runCoreLoop() {
        List<UserConfig> users = db.getConnectedUsers();
        log.info("Core loop starting for {} connected user(s)", users.size());
        for (UserConfig config : users) {
            try {
                runCoreLoopForUser(config);
            } catch (BrokerTokenException e) {
                log.warn("Core loop — user {} skipped: token expired", config.getUser().getId());
            } catch (Exception e) {
                log.error("Core loop — error for user {}: {}", config.getUser().getId(), e.getMessage(), e);
            }
        }
    }

    private void runCoreLoopForUser(UserConfig config) {
        Long userId = config.getUser().getId();
        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        long occupied = db.countActivePositions(userId);
        int slots = config.getMaxPositions() - (int) occupied;
        if (slots <= 0) {
            log.debug("Core loop — user {} has no free slots ({}/{})", userId, occupied, config.getMaxPositions());
            return;
        }

        // Symbols already held (DB + Zerodha holdings) to avoid duplicates
        Set<String> occupiedSymbols = db.getOccupiedSymbols(userId);
        broker.getHoldings().forEach(h -> occupiedSymbols.add(h.symbol()));

        List<Signal> candidates = db.getCandidateSignals(userId, occupiedSymbols);
        if (candidates.isEmpty()) {
            log.debug("Core loop — user {} has no candidates", userId);
            return;
        }

        List<String> symbols = candidates.stream().map(Signal::getSymbol).distinct().toList();
        Map<String, BigDecimal> quotes = broker.getQuotes(symbols);
        List<ScoredSignal> ranked = scoringService.rank(candidates, quotes);
        BigDecimal margin = broker.getAvailableMargin();

        int placed = 0;
        for (ScoredSignal ss : ranked) {
            if (placed >= slots) break;
            try {
                placeEntryOrder(config, broker, ss.signal(), margin);
                placed++;
            } catch (BrokerOrderException e) {
                log.warn("Core loop — order rejected for signal {} user {}: {}",
                        ss.signal().getId(), userId, e.getMessage());
            }
        }
        log.info("Core loop — user {} placed {}/{} orders", userId, placed, slots);
    }

    private void placeEntryOrder(UserConfig config, BrokerAdapter broker,
                                 Signal signal, BigDecimal margin) {
        Long userId = config.getUser().getId();

        // Pre-flight duplicate guard (layer 2 of 3 — DB constraint is layer 1)
        if (db.hasActivePosition(userId, signal.getId())) {
            log.warn("Pre-flight: duplicate position for user {} signal {} — skipping", userId, signal.getId());
            return;
        }

        int qty = sizingService.calculate(config, signal.getEntryPrice(), signal.getStopLoss(), margin);
        if (qty <= 0) {
            log.warn("Insufficient capital: user {} signal {} entry={} — skipping",
                    userId, signal.getId(), signal.getEntryPrice());
            return;
        }

        Long positionId = db.createPendingPosition(config, signal, qty);
        String tag = "pos_" + positionId; // layer 3: Zerodha tag = positionId

        try {
            String orderId = broker.placeLimitOrder(signal.getSymbol(), qty, signal.getEntryPrice(), tag);
            db.recordEntryOrder(positionId, config, signal, orderId, qty);
            events.publishEvent(new OrderPlacedEvent(positionId, signal.getSymbol(), orderId));
            log.info("Entry order placed: pos={} order={} symbol={} qty={} price={}",
                    positionId, orderId, signal.getSymbol(), qty, signal.getEntryPrice());
        } catch (BrokerOrderException e) {
            db.cancelPosition(positionId);
            throw e;
        }
    }

    // ── Fill detection ────────────────────────────────────────────────────────

    public void checkOrderFills() {
        List<Position> pending = db.getPendingEntryPositions();
        if (pending.isEmpty()) return;

        // Group by userId to create one broker adapter per user
        Map<Long, List<Position>> byUser = pending.stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        for (Map.Entry<Long, List<Position>> entry : byUser.entrySet()) {
            db.getUserConfigByUserId(entry.getKey()).ifPresent(config -> {
                try {
                    BrokerAdapter broker = brokerAdapterFactory.forUser(config);
                    for (Position pos : entry.getValue()) {
                        checkFillForPosition(config, broker, pos);
                    }
                } catch (BrokerTokenException e) {
                    log.warn("Fill check — user {} skipped: token expired", entry.getKey());
                }
            });
        }
    }

    private void checkFillForPosition(UserConfig config, BrokerAdapter broker, Position pos) {
        String orderId = pos.getEntryOrderId();
        if (orderId == null) return;

        BrokerOrderDetail detail;
        try {
            detail = broker.getOrderDetail(orderId);
        } catch (BrokerNetworkException e) {
            log.warn("Fill check — could not get order detail for pos {}: {}", pos.getId(), e.getMessage());
            return;
        }

        if (detail.isFullyFilled() || detail.isPartiallyFilled()) {
            handleFill(config, broker, pos, detail);
        } else if (detail.isFailed()) {
            db.markPositionCancelled(pos.getId());
            events.publishEvent(new OrderCancelledEvent(pos.getId(), pos.getSymbol(), orderId, detail.status().name()));
            log.info("Fill check — pos {} order {} {}", pos.getId(), orderId, detail.status());
        } else {
            // Still PENDING — check expiry
            checkExpiry(config, broker, pos, orderId);
        }
    }

    private void handleFill(UserConfig config, BrokerAdapter broker,
                             Position pos, BrokerOrderDetail detail) {
        int filledQty = detail.isPartiallyFilled() ? detail.filledQuantity() : pos.getQuantity();

        // For partial fill: cancel remaining open quantity on Zerodha
        if (detail.isPartiallyFilled()) {
            try { broker.cancelOrder(pos.getEntryOrderId()); }
            catch (Exception e) { log.warn("Could not cancel partial order {}: {}", pos.getEntryOrderId(), e.getMessage()); }
        }

        // Place GTT OCO for the filled quantity
        Signal signal = pos.getSignal();
        String gttId = null;
        try {
            gttId = broker.placeGttOcoOrder(pos.getSymbol(), filledQty,
                    signal.getStopLoss(), signal.getTarget(), "pos_" + pos.getId());
        } catch (BrokerOrderException e) {
            log.error("GTT placement failed for pos {}: {} — position marked ACTIVE without GTT",
                    pos.getId(), e.getMessage());
        }

        db.activatePosition(pos.getId(), filledQty, detail.avgPrice(), gttId);
        events.publishEvent(new OrderFilledEvent(pos.getId(), pos.getSymbol(), pos.getEntryOrderId(),
                filledQty, detail.avgPrice(), gttId));
        log.info("Fill detected: pos={} qty={} avgPrice={} gttId={}", pos.getId(), filledQty, detail.avgPrice(), gttId);
    }

    private void checkExpiry(UserConfig config, BrokerAdapter broker, Position pos, String orderId) {
        if (pos.getOpenedAt() == null) return; // no placed_at stored on position itself
        // Use entry order placed_at via db
        db.getEntryOrder(pos.getId()).ifPresent(order -> {
            long ageDays = ChronoUnit.DAYS.between(
                    order.getPlacedAt().toLocalDate(), LocalDate.now());
            if (ageDays >= config.getOrderExpiryDays()) {
                try { broker.cancelOrder(orderId); } catch (Exception e) {
                    log.warn("Could not cancel expired order {}: {}", orderId, e.getMessage());
                }
                db.markPositionCancelled(pos.getId());
                events.publishEvent(new OrderExpiredEvent(pos.getId(), pos.getSymbol(), orderId));
                log.info("Order expired: pos={} order={} ageDays={}", pos.getId(), orderId, ageDays);
            }
        });
    }

    // ── GTT exit reconciliation ───────────────────────────────────────────────

    public void reconcileGttExits() {
        List<Position> active = db.getActivePositions();
        if (active.isEmpty()) return;

        Map<Long, List<Position>> byUser = active.stream()
                .filter(p -> p.getGttOrderId() != null)
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        for (Map.Entry<Long, List<Position>> entry : byUser.entrySet()) {
            db.getUserConfigByUserId(entry.getKey()).ifPresent(config -> {
                try {
                    BrokerAdapter broker = brokerAdapterFactory.forUser(config);
                    for (Position pos : entry.getValue()) {
                        reconcileGtt(pos, broker);
                    }
                } catch (BrokerTokenException e) {
                    log.warn("GTT reconcile — user {} skipped: token expired", entry.getKey());
                }
            });
        }
    }

    private void reconcileGtt(Position pos, BrokerAdapter broker) {
        GttStatusResult gttStatus;
        try {
            gttStatus = broker.getGttStatus(pos.getGttOrderId());
        } catch (BrokerNetworkException e) {
            log.warn("GTT reconcile — could not fetch GTT {} for pos {}: {}",
                    pos.getGttOrderId(), pos.getId(), e.getMessage());
            return;
        }

        if (!gttStatus.triggered()) return;

        BigDecimal fillPrice = gttStatus.filledPrice();
        Signal signal = pos.getSignal();

        // Determine exit type by comparing fill price to SL vs target
        PositionStatus closeStatus = fillPrice.compareTo(signal.getTarget()) >= 0
                ? PositionStatus.CLOSED_TARGET : PositionStatus.CLOSED_SL;

        // Realised P&L = (fillPrice - avgEntryPrice) × quantity
        BigDecimal pnl = fillPrice.subtract(pos.getAvgEntryPrice())
                .multiply(BigDecimal.valueOf(pos.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        db.closePosition(pos.getId(), closeStatus, pnl);
        events.publishEvent(new PositionClosedEvent(pos.getId(), pos.getSymbol(), closeStatus, pnl));
        log.info("GTT triggered: pos={} symbol={} status={} pnl={}", pos.getId(), pos.getSymbol(), closeStatus, pnl);
    }

    // ── Unmanaged position detection ──────────────────────────────────────────

    public void detectUnmanagedPositions() {
        List<UserConfig> users = db.getConnectedUsers();
        for (UserConfig config : users) {
            try {
                BrokerAdapter broker = brokerAdapterFactory.forUser(config);
                Set<String> systemSymbols = db.getOccupiedSymbols(config.getUser().getId());

                broker.getHoldings().stream()
                        .filter(h -> !systemSymbols.contains(h.symbol()))
                        .forEach(h -> {
                            events.publishEvent(new UnmanagedPositionEvent(
                                    config.getUser().getId(), h.symbol(), h.quantity(), h.lastPrice()));
                            log.warn("Unmanaged position detected: user={} symbol={} qty={}",
                                    config.getUser().getId(), h.symbol(), h.quantity());
                        });
            } catch (BrokerTokenException e) {
                log.warn("Unmanaged check — user {} skipped: token expired", config.getUser().getId());
            }
        }
    }

    // ── Manual exit ───────────────────────────────────────────────────────────

    public void manualExit(Long positionId) {
        Position pos = db.getActivePositions().stream()
                .filter(p -> p.getId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Active position not found: " + positionId));

        UserConfig config = db.getUserConfigByUserId(pos.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("No config for user " + pos.getUser().getId()));

        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        // Cancel GTT so it doesn't fire after we sell
        if (pos.getGttOrderId() != null) {
            try { broker.cancelGttOrder(pos.getGttOrderId()); }
            catch (Exception e) { log.warn("Could not cancel GTT {}: {}", pos.getGttOrderId(), e.getMessage()); }
        }

        // Place CNC market sell order to actually exit the position
        String tag = "pos_" + positionId + "_manual";
        String sellOrderId;
        try {
            sellOrderId = broker.placeMarketSellOrder(pos.getSymbol(), pos.getQuantity(), tag);
            db.recordManualExitOrder(positionId, sellOrderId);
            log.info("Market sell placed: pos={} symbol={} qty={} order={}",
                    positionId, pos.getSymbol(), pos.getQuantity(), sellOrderId);
        } catch (BrokerOrderException e) {
            log.error("Market sell failed for pos={} symbol={}: {} — position closed in DB anyway",
                    positionId, pos.getSymbol(), e.getMessage());
        }

        db.closePosition(positionId, PositionStatus.CLOSED_MANUAL, null);
        events.publishEvent(new PositionClosedEvent(positionId, pos.getSymbol(),
                PositionStatus.CLOSED_MANUAL, null));
        log.info("Manual exit complete: pos={} symbol={}", positionId, pos.getSymbol());
    }
}
