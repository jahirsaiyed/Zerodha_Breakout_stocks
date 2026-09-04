package com.trading.portfolio;

import com.trading.broker.*;
import com.trading.portfolio.dto.OrderPreviewResponse;
import com.trading.portfolio.events.*;
import com.trading.signals.*;
import com.trading.signals.dto.CreateSignalRequest;
import com.trading.signals.dto.SignalResponse;
import com.trading.users.UserConfig;
import com.trading.signals.StopLossBasis;
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
    private final SignalService signalService;

    // ── Core loop ────────────────────────────────────────────────────────────

    public void runCoreLoop() {
        List<UserConfig> users = db.getConnectedUsers();
        log.info("[TRADE] START — {} connected user(s)", users.size());
        for (UserConfig config : users) {
            try {
                runCoreLoopForUser(config);
            } catch (BrokerTokenException e) {
                log.warn("[TRADE] user={} skipped — token expired", config.getUser().getId());
            } catch (Exception e) {
                log.error("[TRADE] user={} error: {}", config.getUser().getId(), e.getMessage(), e);
            }
        }
    }

    private void runCoreLoopForUser(UserConfig config) {
        Long userId = config.getUser().getId();

        if (Boolean.TRUE.equals(config.getTradingPaused())) {
            log.info("[TRADE] user={} skipped — trading paused", userId);
            return;
        }

        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        long occupied = db.countActivePositions(userId);
        int slots = config.getMaxPositions() - (int) occupied;
        log.info("[TRADE] user={} slots={}/{} (occupied={})", userId, slots, config.getMaxPositions(), occupied);
        if (slots <= 0) {
            log.info("[TRADE] user={} no free slots — skipping", userId);
            return;
        }

        // Symbols already held (DB + Zerodha holdings) to avoid duplicates.
        // If holdings fetch fails (network error), proceed conservatively with DB symbols only.
        Set<String> occupiedSymbols = db.getOccupiedSymbols(userId);
        try {
            broker.getHoldings().forEach(h -> occupiedSymbols.add(h.symbol()));
        } catch (BrokerNetworkException e) {
            log.warn("Core loop — could not fetch Zerodha holdings for user {}: {}, proceeding with DB symbols only",
                    userId, e.getMessage());
        }

        if (Boolean.TRUE.equals(config.getSyncPaused())) {
            log.info("[TRADE] user={} skipped — signal sync paused", userId);
            return;
        }

        List<Signal> candidates = db.getCandidateSignals(userId, occupiedSymbols);
        log.info("[TRADE] user={} occupiedSymbols={} candidates={}", userId, occupiedSymbols.size(), candidates.size());
        if (candidates.isEmpty()) {
            log.info("[TRADE] user={} no candidates — skipping", userId);
            return;
        }

        List<String> symbols = candidates.stream().map(Signal::getSymbol).distinct().toList();
        Map<String, BigDecimal> quotes;
        try {
            quotes = broker.getQuotes(symbols);
            log.info("[TRADE] user={} broker quotes fetched for {}/{} symbols", userId, quotes.size(), symbols.size());
        } catch (BrokerTokenException e) {
            log.warn("[TRADE] user={} quotes unavailable (permission denied) — falling back to Google Finance", userId);
            quotes = Map.of();
        } catch (BrokerNetworkException e) {
            log.warn("[TRADE] user={} quotes fetch failed: {} — falling back to Google Finance", userId, e.getMessage());
            quotes = Map.of();
        }
        List<ScoredSignal> ranked = scoringService.rank(candidates, quotes);
        log.info("[TRADE] user={} ranked={} signal(s)", userId, ranked.size());
        BigDecimal margin;
        try {
            margin = broker.getAvailableMargin();
            log.info("[TRADE] user={} available margin=₹{}", userId, margin);
        } catch (BrokerNetworkException e) {
            log.warn("[TRADE] user={} could not fetch margin: {} — skipping order placement", userId, e.getMessage());
            return;
        }

        int placed = 0;
        for (ScoredSignal ss : ranked) {
            if (placed >= slots) break;
            try {
                placeEntryOrder(config, broker, ss.signal(), margin);
                placed++;
            } catch (BrokerException e) {
                // Catch all broker errors (order rejection, network, config) per signal so a
                // failure on one signal does not abort placement of the remaining ranked signals.
                log.warn("[TRADE] user={} order failed for signal={}: {}", userId, ss.signal().getId(), e.getMessage());
            }
        }
        log.info("[TRADE] DONE user={} placed={}/{} slot(s)", userId, placed, slots);
    }

    private Long placeEntryOrder(UserConfig config, BrokerAdapter broker,
                                 Signal signal, BigDecimal margin) {
        Long userId = config.getUser().getId();

        // Pre-flight duplicate guard (layer 2 of 3 — DB constraint is layer 1)
        if (db.hasActivePosition(userId, signal.getId())) {
            log.warn("Pre-flight: duplicate position for user {} signal {} — skipping", userId, signal.getId());
            return null;
        }

        int qty = sizingService.calculate(config, signal.getEntryPrice(), signal.getStopLoss(), margin);
        if (qty <= 0) {
            log.warn("[TRADE] user={} signal={} symbol={} entry={} — insufficient capital, skipping",
                    userId, signal.getId(), signal.getSymbol(), signal.getEntryPrice());
            return null;
        }

        log.info("[TRADE] placing order: user={} signal={} symbol={} qty={} entry={}",
                userId, signal.getId(), signal.getSymbol(), qty, signal.getEntryPrice());
        Long positionId = db.createPendingPosition(config, signal, qty);
        String tag = "pos_" + positionId; // layer 3: Zerodha tag = positionId

        try {
            String orderId = broker.placeLimitOrder(signal.getSymbol(), qty, signal.getEntryPrice(), tag);
            db.recordEntryOrder(positionId, config, signal, orderId, qty);
            events.publishEvent(new OrderPlacedEvent(positionId, signal.getSymbol(), orderId));
            log.info("[TRADE] order placed: pos={} orderId={} symbol={} qty={} price={}",
                    positionId, orderId, signal.getSymbol(), qty, signal.getEntryPrice());
            return positionId;
        } catch (BrokerException e) {
            // Any broker failure (order rejection OR network error) must roll back the pending position.
            // Without this, a failed placeLimitOrder leaves a stranded PENDING_ENTRY row in the DB.
            db.cancelPosition(positionId);
            log.error("[TRADE] order placement failed for pos={} signal={}: {}", positionId, signal.getId(), e.getMessage());
            throw e;
        }
    }

    // ── Manual order placement (user-initiated from signals screen) ───────────

    /**
     * Returns a preview of the order that would be placed for the given signal,
     * including estimated quantity, cost, and any blocking reason if canPlace=false.
     */
    public OrderPreviewResponse previewOrderForSignal(UserConfig config, Long signalId) {
        Long userId = config.getUser().getId();

        Signal signal = db.getSignalById(signalId)
                .orElseThrow(() -> new IllegalArgumentException("Signal not found: " + signalId));

        if (signal.getStatus() != SignalStatus.ACTIVE) {
            return blocked(signal, 0, null, "Signal is no longer active");
        }
        if (Boolean.TRUE.equals(config.getTradingPaused())) {
            return blocked(signal, 0, null, "Trading is paused — enable it in Settings");
        }
        if (db.hasActivePosition(userId, signalId)) {
            return blocked(signal, 0, null, "You already have an open position for this signal");
        }
        if (db.getOccupiedSymbols(userId).contains(signal.getSymbol())) {
            return blocked(signal, 0, null, "You already hold " + signal.getSymbol());
        }

        long occupied = db.countActivePositions(userId);
        int slots = config.getMaxPositions() - (int) occupied;
        if (slots <= 0) {
            return blocked(signal, 0, null, "No free slots (max " + config.getMaxPositions() + " positions)");
        }

        if (!Boolean.TRUE.equals(config.getZerodhaConnected())) {
            return blocked(signal, slots, null, "Zerodha account not connected");
        }

        BrokerAdapter broker;
        BigDecimal margin;
        try {
            broker = brokerAdapterFactory.forUser(config);
            margin = broker.getAvailableMargin();
        } catch (BrokerTokenException e) {
            return blocked(signal, slots, null, "Zerodha session expired — please reconnect");
        } catch (BrokerNetworkException e) {
            return blocked(signal, slots, null, "Could not fetch available margin: " + e.getMessage());
        }

        String warning = null;
        try {
            BigDecimal ltp = broker.getQuotes(List.of(signal.getSymbol())).get(signal.getSymbol());
            if (ltp != null && ltp.compareTo(signal.getEntryPrice()) > 0) {
                warning = "Current price ₹" + ltp.toPlainString() + " is above entry ₹"
                        + signal.getEntryPrice().toPlainString()
                        + ". A limit order at ₹" + signal.getEntryPrice().toPlainString()
                        + " will only fill if price drops back to the entry level.";
            }
        } catch (BrokerTokenException e) {
            log.warn("[PREVIEW] quotes unavailable (permission denied) for {} — skipping entry warning", signal.getSymbol());
        } catch (BrokerNetworkException e) {
            log.warn("[PREVIEW] could not fetch LTP for {} to validate entry: {}", signal.getSymbol(), e.getMessage());
        }

        int qty = sizingService.calculate(config, signal.getEntryPrice(), signal.getStopLoss(), margin);
        if (qty <= 0) {
            return blocked(signal, slots, margin,
                    "Insufficient margin to buy even 1 share at ₹" + signal.getEntryPrice().toPlainString());
        }

        BigDecimal cost = signal.getEntryPrice().multiply(BigDecimal.valueOf(qty));
        return new OrderPreviewResponse(
                signal.getId(), signal.getSymbol(),
                signal.getEntryPrice(), signal.getStopLoss(), signal.getTarget(), signal.getRiskRewardRatio(),
                qty, cost, slots, margin, true, null, warning);
    }

    /**
     * Places a limit entry order for the given signal on behalf of the user.
     * Returns the newly created position ID.
     */
    public Long placeOrderForSignal(UserConfig config, Long signalId) {
        Signal signal = validateEntryGuardrails(config, signalId);

        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        BigDecimal margin;
        try {
            margin = broker.getAvailableMargin();
        } catch (BrokerNetworkException e) {
            throw new IllegalStateException("Could not fetch available margin: " + e.getMessage());
        }

        Long positionId = placeEntryOrder(config, broker, signal, margin);
        if (positionId == null) {
            throw new IllegalStateException("Order was not placed — insufficient margin or duplicate position");
        }
        return positionId;
    }

    /**
     * Shared entry guardrails for both automated ({@link #placeOrderForSignal}) and manually-recorded
     * ({@link #recordManualOrder}) entries: trading not paused, signal ACTIVE, no duplicate position
     * for this signal, symbol not already held, and a free position slot available.
     */
    private Signal validateEntryGuardrails(UserConfig config, Long signalId) {
        Long userId = config.getUser().getId();

        if (Boolean.TRUE.equals(config.getTradingPaused())) {
            throw new IllegalStateException("Trading is paused — enable it in Settings");
        }

        Signal signal = db.getSignalById(signalId)
                .orElseThrow(() -> new IllegalArgumentException("Signal not found: " + signalId));
        if (signal.getStatus() != SignalStatus.ACTIVE) {
            throw new IllegalArgumentException("Signal is not active");
        }
        if (db.hasActivePosition(userId, signalId)) {
            throw new IllegalStateException("You already have an open position for this signal");
        }
        if (db.getOccupiedSymbols(userId).contains(signal.getSymbol())) {
            throw new IllegalStateException("You already hold " + signal.getSymbol());
        }

        long occupied = db.countActivePositions(userId);
        if (occupied >= config.getMaxPositions()) {
            throw new IllegalStateException("No free position slots (max " + config.getMaxPositions() + ")");
        }
        return signal;
    }

    // ── Manual order recording ──────────────────────────────────────────────

    /**
     * Records a fill the user already executed manually in Zerodha (outside our broker integration) —
     * no order is placed. Applies the same guardrails as {@link #placeOrderForSignal} so a manual entry
     * can't bypass max-positions/duplicate-symbol/trading-paused invariants, then reuses {@link #handleFill}
     * to place the GTT target and activate the position exactly like an automated fill.
     */
    public Long recordManualOrder(UserConfig config, Long signalId, int quantity, BigDecimal avgPrice) {
        Signal signal = validateEntryGuardrails(config, signalId);
        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        Long positionId = db.createPendingPosition(config, signal, quantity, EntrySource.MANUAL);
        db.recordManualEntryOrder(positionId, config, signal, quantity, avgPrice);
        Position pos = db.getPositionById(positionId).orElseThrow();

        log.info("[MANUAL-ENTRY] recording pos={} symbol={} qty={} avgPrice={}",
                positionId, signal.getSymbol(), quantity, avgPrice);
        handleFill(config, broker, pos, new BrokerOrderDetail(BrokerOrderStatus.COMPLETE, quantity, avgPrice));

        return positionId;
    }

    /**
     * Same as {@link #recordManualOrder}, for a trade with no pre-existing tracked signal: creates a
     * MANUAL {@link Signal} first, then records the fill against it. If recording fails after the signal
     * was created, the signal is cancelled rather than left ACTIVE and unowned, where the next core-loop
     * cycle could otherwise pick it up and place a real order for it.
     */
    public Long recordManualOrderForNewSignal(UserConfig config, CreateSignalRequest signalRequest,
                                              int quantity, BigDecimal avgPrice) {
        SignalResponse created = signalService.create(signalRequest);
        try {
            return recordManualOrder(config, created.id(), quantity, avgPrice);
        } catch (RuntimeException e) {
            try {
                signalService.cancel(created.id());
            } catch (RuntimeException cleanupError) {
                log.warn("[MANUAL-ENTRY] could not auto-cancel signal {} after failed recording: {}",
                        created.id(), cleanupError.getMessage());
            }
            throw e;
        }
    }

    private OrderPreviewResponse blocked(Signal signal, int slots, BigDecimal margin, String reason) {
        return new OrderPreviewResponse(
                signal.getId(), signal.getSymbol(),
                signal.getEntryPrice(), signal.getStopLoss(), signal.getTarget(), signal.getRiskRewardRatio(),
                0, BigDecimal.ZERO, slots, margin, false, reason, null);
    }

    // ── Fill detection ────────────────────────────────────────────────────────

    public void checkOrderFills() {
        List<Position> pending = db.getPendingEntryPositions();
        if (pending.isEmpty()) return;

        // Group by userId to create one broker adapter per user
        Map<Long, List<Position>> byUser = pending.stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        log.info("[FILL] START — {} pending position(s) across {} user(s)", pending.size(), byUser.size());
        for (Map.Entry<Long, List<Position>> entry : byUser.entrySet()) {
            db.getUserConfigByUserId(entry.getKey()).ifPresent(config -> {
                try {
                    BrokerAdapter broker = brokerAdapterFactory.forUser(config);
                    for (Position pos : entry.getValue()) {
                        try {
                            checkFillForPosition(config, broker, pos);
                        } catch (Exception e) {
                            log.error("[FILL] pos={} symbol={} error: {}", pos.getId(), pos.getSymbol(), e.getMessage(), e);
                        }
                    }
                } catch (BrokerTokenException e) {
                    log.warn("[FILL] user={} skipped — token expired", entry.getKey());
                }
            });
        }
    }

    private void checkFillForPosition(UserConfig config, BrokerAdapter broker, Position pos) {
        String orderId = pos.getEntryOrderId();
        if (orderId == null) return;

        log.info("[FILL] checking pos={} symbol={} order={}", pos.getId(), pos.getSymbol(), orderId);
        BrokerOrderDetail detail;
        try {
            detail = broker.getOrderDetail(orderId);
        } catch (BrokerOrderException e) {
            // Order aged out of Zerodha's day-scoped order book (e.g. "Couldn't find that order_id").
            // It can never be looked up again this way, and holdings alone can't prove this order
            // caused them (a pre-existing manual holding in the same symbol would look identical) —
            // guessing FILLED here could place a live GTT sell against shares we don't actually own
            // for this position. Leave the position untouched and ask a human to reconcile it.
            log.error("[FILL] order={} not found for pos={} symbol={} (aged past trading day) — needs manual reconciliation: {}",
                    orderId, pos.getId(), pos.getSymbol(), e.getMessage());
            events.publishEvent(new OrderLookupFailedEvent(pos.getId(), pos.getSymbol(), orderId));
            return;
        } catch (BrokerNetworkException e) {
            log.warn("[FILL] could not get order detail for pos={}: {}", pos.getId(), e.getMessage());
            return;
        }

        if (detail.isFullyFilled() || detail.isPartiallyFilled()) {
            handleFill(config, broker, pos, detail);
        } else if (detail.isFailed()) {
            db.markPositionCancelled(pos.getId());
            events.publishEvent(new OrderCancelledEvent(pos.getId(), pos.getSymbol(), orderId, detail.status().name()));
            log.info("[FILL] CANCELLED pos={} symbol={} order={} status={}", pos.getId(), pos.getSymbol(), orderId, detail.status());
        } else {
            // Still PENDING — check expiry
            checkExpiry(config, broker, pos, orderId);
        }
    }

    private void handleFill(UserConfig config, BrokerAdapter broker,
                             Position pos, BrokerOrderDetail detail) {
        int filledQty = detail.filledQuantity() > 0 ? detail.filledQuantity() : pos.getQuantity();

        // For partial fill: cancel remaining open quantity on Zerodha
        if (detail.isPartiallyFilled()) {
            try { broker.cancelOrder(pos.getEntryOrderId()); }
            catch (Exception e) { log.warn("Could not cancel partial order {}: {}", pos.getEntryOrderId(), e.getMessage()); }
        }

        // Place single-leg GTT for HALF the filled quantity — on trigger only 50% is sold;
        // the remaining half stays ACTIVE with SL moved to breakeven (avgEntryPrice).
        Signal signal = pos.getSignal();
        int halfQty = Math.max(1, filledQty / 2);
        String gttId = null;
        try {
            gttId = broker.placeGttTargetOrder(pos.getSymbol(), halfQty,
                    signal.getTarget(), "pos_" + pos.getId());
        } catch (BrokerException e) {
            // Any broker failure placing the GTT (rejected order, expired token, network) must not
            // block recording the fill itself — the entry already happened, so activate without a GTT.
            log.error("GTT target placement failed for pos {}: {} — position marked ACTIVE without GTT",
                    pos.getId(), e.getMessage());
        }

        db.activatePosition(pos.getId(), filledQty, detail.avgPrice(), gttId);
        events.publishEvent(new OrderFilledEvent(pos.getId(), pos.getSymbol(), pos.getEntryOrderId(),
                filledQty, detail.avgPrice(), gttId));
        log.info("[FILL] FILLED pos={} symbol={} qty={} avgPrice={} gttId={}", pos.getId(), pos.getSymbol(), filledQty, detail.avgPrice(), gttId);
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
                log.info("[FILL] EXPIRED pos={} symbol={} order={} ageDays={}", pos.getId(), pos.getSymbol(), orderId, ageDays);
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

        log.info("[GTT] START — {} active position(s) with GTT across {} user(s)",
                byUser.values().stream().mapToInt(List::size).sum(), byUser.size());
        for (Map.Entry<Long, List<Position>> entry : byUser.entrySet()) {
            db.getUserConfigByUserId(entry.getKey()).ifPresent(config -> {
                try {
                    BrokerAdapter broker = brokerAdapterFactory.forUser(config);
                    for (Position pos : entry.getValue()) {
                        reconcileGtt(pos, broker);
                    }
                } catch (BrokerTokenException e) {
                    log.warn("[GTT] user={} skipped — token expired", entry.getKey());
                }
            });
        }
    }

    private void reconcileGtt(Position pos, BrokerAdapter broker) {
        log.info("[GTT] checking pos={} symbol={} gttId={}", pos.getId(), pos.getSymbol(), pos.getGttOrderId());
        GttStatusResult gttStatus;
        try {
            gttStatus = broker.getGttStatus(pos.getGttOrderId());
        } catch (BrokerNetworkException e) {
            log.warn("[GTT] could not fetch gttId={} for pos={}: {}", pos.getGttOrderId(), pos.getId(), e.getMessage());
            return;
        }

        if (!gttStatus.triggered()) return;

        BigDecimal fillPrice = gttStatus.filledPrice();

        // GTT is single-leg target-only — a trigger always means target was reached.
        // The GTT was placed for floor(qty/2) shares; remaining = qty - soldQty stays ACTIVE.
        int soldQty = Math.max(1, pos.getQuantity() / 2);
        int remainingQty = pos.getQuantity() - soldQty;

        if (remainingQty <= 0) {
            // qty=1 edge case: sell the only share → close fully
            BigDecimal pnl = fillPrice.subtract(pos.getAvgEntryPrice())
                    .multiply(BigDecimal.valueOf(soldQty))
                    .setScale(2, RoundingMode.HALF_UP);
            db.closePosition(pos.getId(), PositionStatus.CLOSED_TARGET, pnl);
            events.publishEvent(new PositionClosedEvent(pos.getId(), pos.getSymbol(), PositionStatus.CLOSED_TARGET, pnl));
            log.info("[GTT] CLOSED_TARGET (full) pos={} symbol={} pnl={}", pos.getId(), pos.getSymbol(), pnl);
            return;
        }

        // Partial exit: sold soldQty shares; keep remaining half ACTIVE at breakeven SL.
        BigDecimal breakevenSl = pos.getAvgEntryPrice();
        db.partialExitPosition(pos.getId(), remainingQty, breakevenSl);
        events.publishEvent(new TargetPartialExitEvent(
                pos.getId(), pos.getSymbol(), soldQty, remainingQty, fillPrice, breakevenSl));
        log.info("[GTT] PARTIAL_TARGET pos={} symbol={} sold={} remaining={} fillPrice={} breakevenSl={}",
                pos.getId(), pos.getSymbol(), soldQty, remainingQty, fillPrice, breakevenSl);
    }

    // ── Closing-basis stop-loss check ─────────────────────────────────────────

    /**
     * Checks all ACTIVE positions whose signal has the given {@link StopLossBasis}.
     * At the moment this runs (scheduled to align with candle closes), the current LTP
     * approximates the closing price for that timeframe. If LTP is below the signal's
     * stop-loss, a market sell is placed immediately and the position is closed as SL.
     */
    public void checkClosingBasisStopLoss(StopLossBasis basis) {
        List<Position> positions = db.getActivePositionsByBasis(basis);
        if (positions.isEmpty()) return;

        Map<Long, List<Position>> byUser = positions.stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        log.info("[SL-{}] START — {} active position(s) across {} user(s)", basis, positions.size(), byUser.size());
        for (Map.Entry<Long, List<Position>> entry : byUser.entrySet()) {
            db.getUserConfigByUserId(entry.getKey()).ifPresent(config -> {
                try {
                    BrokerAdapter broker = brokerAdapterFactory.forUser(config);
                    List<String> symbols = entry.getValue().stream()
                            .map(Position::getSymbol).distinct().toList();
                    Map<String, BigDecimal> quotes = fetchQuotesSafe(broker, symbols, entry.getKey(), basis.name());
                    for (Position pos : entry.getValue()) {
                        try {
                            checkSlForPosition(pos, broker, quotes, basis);
                        } catch (Exception e) {
                            log.error("[SL-{}] pos={} symbol={} error: {}", basis, pos.getId(), pos.getSymbol(), e.getMessage(), e);
                        }
                    }
                } catch (BrokerTokenException e) {
                    log.warn("[SL-{}] user={} skipped — token expired", basis, entry.getKey());
                }
            });
        }
        log.info("[SL-{}] DONE", basis);
    }

    private Map<String, BigDecimal> fetchQuotesSafe(BrokerAdapter broker, List<String> symbols,
                                                     Long userId, String tag) {
        try {
            return broker.getQuotes(symbols);
        } catch (BrokerTokenException e) {
            log.warn("[SL-{}] user={} quotes unavailable (permission denied) — skipping SL check", tag, userId);
            return Map.of();
        } catch (BrokerNetworkException e) {
            log.warn("[SL-{}] user={} quotes fetch failed: {} — skipping SL check", tag, userId, e.getMessage());
            return Map.of();
        }
    }

    private void checkSlForPosition(Position pos, BrokerAdapter broker,
                                    Map<String, BigDecimal> quotes, StopLossBasis basis) {
        BigDecimal ltp = quotes.get(pos.getSymbol());
        if (ltp == null) {
            log.warn("[SL-{}] no LTP for pos={} symbol={} — skipping", basis, pos.getId(), pos.getSymbol());
            return;
        }
        Signal signal = pos.getSignal();
        BigDecimal effectiveSl = pos.getBreakevenSl() != null ? pos.getBreakevenSl() : signal.getStopLoss();
        if (ltp.compareTo(effectiveSl) >= 0) return; // SL not breached

        log.info("[SL-{}] effective SL for pos={} is {} ({})", basis, pos.getId(), effectiveSl,
                pos.getBreakevenSl() != null ? "breakeven" : "signal");

        log.info("[SL-{}] TRIGGERED pos={} symbol={} ltp={} sl={} — placing market sell",
                basis, pos.getId(), pos.getSymbol(), ltp, effectiveSl);

        // Cancel target GTT so it doesn't fire after we sell
        if (pos.getGttOrderId() != null) {
            try { broker.cancelGttOrder(pos.getGttOrderId()); }
            catch (Exception e) { log.warn("[SL-{}] could not cancel GTT {} for pos={}: {}",
                    basis, pos.getGttOrderId(), pos.getId(), e.getMessage()); }
        }

        String tag = "pos_" + pos.getId() + "_sl";
        try {
            String sellOrderId = broker.placeMarketSellOrder(pos.getSymbol(), pos.getQuantity(), tag);
            db.recordManualExitOrder(pos.getId(), sellOrderId);
            log.info("[SL-{}] market sell placed pos={} symbol={} qty={} order={}",
                    basis, pos.getId(), pos.getSymbol(), pos.getQuantity(), sellOrderId);
        } catch (BrokerException e) {
            log.error("[SL-{}] market sell FAILED pos={} symbol={}: {}", basis, pos.getId(), pos.getSymbol(), e.getMessage());
            return; // do not close position in DB if broker call failed
        }

        BigDecimal pnl = ltp.subtract(pos.getAvgEntryPrice())
                .multiply(BigDecimal.valueOf(pos.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        db.closePosition(pos.getId(), PositionStatus.CLOSED_SL, pnl);
        events.publishEvent(new PositionClosedEvent(pos.getId(), pos.getSymbol(), PositionStatus.CLOSED_SL, pnl));
        log.info("[SL-{}] CLOSED pos={} symbol={} pnl={}", basis, pos.getId(), pos.getSymbol(), pnl);
    }

    // ── Unmanaged position detection ──────────────────────────────────────────

    public void detectUnmanagedPositions() {
        List<UserConfig> users = db.getConnectedUsers();
        log.info("[UNMANAGED] scanning {} connected user(s)", users.size());
        for (UserConfig config : users) {
            try {
                BrokerAdapter broker = brokerAdapterFactory.forUser(config);
                Set<String> systemSymbols = db.getOccupiedSymbols(config.getUser().getId());

                broker.getHoldings().stream()
                        .filter(h -> !systemSymbols.contains(h.symbol()))
                        .forEach(h -> {
                            events.publishEvent(new UnmanagedPositionEvent(
                                    config.getUser().getId(), h.symbol(), h.quantity(), h.lastPrice()));
                            log.warn("[UNMANAGED] user={} symbol={} qty={} — not tracked in DB",
                                    config.getUser().getId(), h.symbol(), h.quantity());
                        });
            } catch (BrokerTokenException e) {
                log.warn("[UNMANAGED] user={} skipped — token expired", config.getUser().getId());
            }
        }
    }

    // ── Manual cancel (pending) ───────────────────────────────────────────────

    public void cancelPendingPosition(Long positionId) {
        Position pos = db.getPendingEntryPositions().stream()
                .filter(p -> p.getId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pending position not found: " + positionId));

        UserConfig config = db.getUserConfigByUserId(pos.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("No config for user " + pos.getUser().getId()));

        log.info("[CANCEL] START pos={} symbol={} user={}", positionId, pos.getSymbol(), pos.getUser().getId());
        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        String orderId = pos.getEntryOrderId();
        if (orderId != null) {
            try {
                broker.cancelOrder(orderId);
                log.info("[CANCEL] entry order cancelled pos={} order={}", positionId, orderId);
            } catch (Exception e) {
                log.warn("[CANCEL] could not cancel entry order {} for pos={}: {}", orderId, positionId, e.getMessage());
            }
        }

        db.markPositionCancelled(positionId);
        events.publishEvent(new OrderCancelledEvent(positionId, pos.getSymbol(), orderId, "manual_cancel"));
        log.info("[CANCEL] DONE pos={} symbol={}", positionId, pos.getSymbol());
    }

    // ── Manual fill confirmation (admin) ────────────────────────────────────────

    /**
     * Manually confirms a fill for a pending position whose order can no longer be verified
     * with Zerodha (e.g. aged past the trading day — see {@link com.trading.portfolio.events.OrderLookupFailedEvent}).
     * The caller is asserting ground truth from Zerodha's own order/holdings view; this never
     * queries the broker to decide fill status, only to place the resulting GTT target order.
     */
    public Long confirmManualFill(Long positionId, int quantity, BigDecimal avgPrice) {
        Position pos = db.getPendingEntryPositions().stream()
                .filter(p -> p.getId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pending position not found: " + positionId));

        UserConfig config = db.getUserConfigByUserId(pos.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("No config for user " + pos.getUser().getId()));

        log.info("[CONFIRM-FILL] START pos={} symbol={} qty={} avgPrice={}",
                positionId, pos.getSymbol(), quantity, avgPrice);
        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        handleFill(config, broker, pos, new BrokerOrderDetail(BrokerOrderStatus.COMPLETE, quantity, avgPrice));

        log.info("[CONFIRM-FILL] DONE pos={} symbol={}", positionId, pos.getSymbol());
        return positionId;
    }

    // ── Manual exit ───────────────────────────────────────────────────────────

    public void manualExit(Long positionId) {
        Position pos = db.getActivePositions().stream()
                .filter(p -> p.getId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Active position not found: " + positionId));

        UserConfig config = db.getUserConfigByUserId(pos.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("No config for user " + pos.getUser().getId()));

        log.info("[MANUAL] START pos={} symbol={} user={}", positionId, pos.getSymbol(), pos.getUser().getId());
        BrokerAdapter broker = brokerAdapterFactory.forUser(config);

        // Cancel GTT so it doesn't fire after we sell
        if (pos.getGttOrderId() != null) {
            try { broker.cancelGttOrder(pos.getGttOrderId()); }
            catch (Exception e) { log.warn("[MANUAL] could not cancel GTT {}: {}", pos.getGttOrderId(), e.getMessage()); }
        }

        // Place CNC market sell order to actually exit the position.
        // Do NOT mark the DB position closed if the broker call fails — the stock is still held.
        String tag = "pos_" + positionId + "_manual";
        String sellOrderId = broker.placeMarketSellOrder(pos.getSymbol(), pos.getQuantity(), tag);
        db.recordManualExitOrder(positionId, sellOrderId);
        log.info("[MANUAL] market sell placed pos={} symbol={} qty={} order={}",
                positionId, pos.getSymbol(), pos.getQuantity(), sellOrderId);

        db.closePosition(positionId, PositionStatus.CLOSED_MANUAL, null);
        events.publishEvent(new PositionClosedEvent(positionId, pos.getSymbol(),
                PositionStatus.CLOSED_MANUAL, null));
        log.info("[MANUAL] DONE pos={} symbol={}", positionId, pos.getSymbol());
    }
}
