package com.trading.portfolio;

import com.trading.broker.*;
import com.trading.portfolio.dto.OrderPreviewResponse;
import com.trading.signals.*;
import com.trading.signals.StopLossBasis;
import com.trading.users.PositionSizingMethod;
import com.trading.users.User;
import com.trading.users.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioEngineTest {

    @Mock private PortfolioDbService db;
    @Mock private BrokerAdapterFactory brokerAdapterFactory;
    @Mock private SignalScoringService scoringService;
    @Mock private PositionSizingService sizingService;
    @Mock private ApplicationEventPublisher events;
    @Mock private BrokerAdapter broker;

    @InjectMocks
    private PortfolioEngine engine;

    private UserConfig userConfig;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").name("Test").passwordHash("x").build();
        userConfig = UserConfig.builder()
                .user(user)
                .maxPositions(5)
                .positionSizingMethod(PositionSizingMethod.FIXED)
                .positionSizingValue(BigDecimal.valueOf(10_000))
                .orderExpiryDays(5)
                .build();
    }

    // ── runCoreLoop ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("runCoreLoop skips user with no free slots")
    void runCoreLoop_noFreeSlots_skipsUser() {
        when(db.getConnectedUsers()).thenReturn(List.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(db.countActivePositions(1L)).thenReturn(5L); // fully occupied

        engine.runCoreLoop();

        verify(db, never()).getCandidateSignals(anyLong(), any());
    }

    @Test
    @DisplayName("runCoreLoop places orders for candidates up to free slots")
    void runCoreLoop_withSlots_placesOrders() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        ScoredSignal scored = new ScoredSignal(signal, BigDecimal.valueOf(0.8));

        when(db.getConnectedUsers()).thenReturn(List.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(db.countActivePositions(1L)).thenReturn(4L); // 1 slot free
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(broker.getHoldings()).thenReturn(List.of());
        when(db.getCandidateSignals(anyLong(), any())).thenReturn(List.of(signal));
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2450)));
        when(scoringService.rank(any(), any())).thenReturn(List.of(scored));
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);
        when(db.createPendingPosition(any(), any(), anyInt())).thenReturn(10L);
        when(broker.placeLimitOrder(anyString(), anyInt(), any(), anyString())).thenReturn("ORD123");

        engine.runCoreLoop();

        verify(broker).placeLimitOrder(eq("RELIANCE"), eq(4), any(BigDecimal.class), eq("pos_10"));
        verify(db).recordEntryOrder(10L, userConfig, signal, "ORD123", 4);
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("runCoreLoop handles BrokerTokenException gracefully")
    void runCoreLoop_tokenExpired_logsAndContinues() {
        when(db.getConnectedUsers()).thenReturn(List.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenThrow(new BrokerTokenException("expired"));

        // Should not throw
        engine.runCoreLoop();

        verify(db, never()).countActivePositions(anyLong());
    }

    // ── checkOrderFills ──────────────────────────────────────────────────────

    @Test
    @DisplayName("checkOrderFills activates position on full fill")
    void checkOrderFills_fullFill_activatesPosition() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildPosition(10L, user, "RELIANCE", "ORD123");
        pos.setSignal(signal);
        BrokerOrderDetail detail = new BrokerOrderDetail(BrokerOrderStatus.COMPLETE, 4, BigDecimal.valueOf(2410));

        when(db.getPendingEntryPositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getOrderDetail("ORD123")).thenReturn(detail);
        when(broker.placeGttTargetOrder(anyString(), anyInt(), any(), anyString())).thenReturn("GTT456");

        engine.checkOrderFills();

        verify(broker).placeGttTargetOrder(eq("RELIANCE"), eq(2), any(BigDecimal.class), eq("pos_10")); // GTT for half qty
        verify(broker, never()).placeGttOcoOrder(anyString(), anyInt(), any(), any(), anyString());
        verify(db).activatePosition(10L, 4, BigDecimal.valueOf(2410), "GTT456");
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderFilledEvent.class));
    }

    @Test
    @DisplayName("checkOrderFills cancels position on rejected order")
    void checkOrderFills_rejectedOrder_cancelsPosition() {
        Position pos = buildPosition(10L, user, "RELIANCE", "ORD123");
        BrokerOrderDetail detail = new BrokerOrderDetail(BrokerOrderStatus.REJECTED, 0, null);

        when(db.getPendingEntryPositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getOrderDetail("ORD123")).thenReturn(detail);

        engine.checkOrderFills();

        verify(db).markPositionCancelled(10L);
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("checkOrderFills cancels position when Zerodha auto-cancelled order at EOD")
    void checkOrderFills_cancelledOrder_cancelsPosition() {
        Position pos = buildPosition(10L, user, "RELIANCE", "ORD123");
        BrokerOrderDetail detail = new BrokerOrderDetail(BrokerOrderStatus.CANCELLED, 0, null);

        when(db.getPendingEntryPositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getOrderDetail("ORD123")).thenReturn(detail);

        engine.checkOrderFills();

        verify(db).markPositionCancelled(10L);
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("checkOrderFills returns early when no pending positions")
    void checkOrderFills_noPendingPositions_returnsEarly() {
        when(db.getPendingEntryPositions()).thenReturn(List.of());

        engine.checkOrderFills();

        verify(db, never()).getUserConfigByUserId(anyLong());
    }

    @Test
    @DisplayName("checkOrderFills leaves position untouched and alerts when order aged out of Zerodha's order book")
    void checkOrderFills_orderNotFound_leavesPositionUntouchedAndAlerts() {
        Position pos = buildPosition(10L, user, "RELIANCE", "ORD123");

        when(db.getPendingEntryPositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getOrderDetail("ORD123")).thenThrow(new BrokerOrderException("Zerodha [GeneralException]: Couldn't find that `order_id`."));

        engine.checkOrderFills();

        // Must never guess FILLED/CANCELLED from this alone — a pre-existing manual holding in the
        // same symbol would look identical, and guessing FILLED would place a live GTT sell order
        // against shares this position never actually bought.
        verify(db, never()).activatePosition(anyLong(), anyInt(), any(), any());
        verify(db, never()).markPositionCancelled(anyLong());
        verify(broker, never()).placeGttTargetOrder(anyString(), anyInt(), any(), anyString());
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderLookupFailedEvent.class));
    }

    // ── reconcileGttExits ────────────────────────────────────────────────────

    @Test
    @DisplayName("reconcileGttExits partial exit: qty=4 triggered → sells 2, keeps 2 ACTIVE at breakeven SL")
    void reconcileGttExits_targetHit_qty4_partialExit() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));
        GttStatusResult triggered = new GttStatusResult(true, BigDecimal.valueOf(2605));

        when(db.getActivePositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getGttStatus("GTT456")).thenReturn(triggered);

        engine.reconcileGttExits();

        // soldQty = 4/2 = 2, remainingQty = 2 — partial exit, NOT a full close
        verify(db).partialExitPosition(eq(10L), eq(2), any(BigDecimal.class));
        verify(db, never()).closePosition(any(), any(), any());
        verify(events).publishEvent(any(com.trading.portfolio.events.TargetPartialExitEvent.class));
    }

    @Test
    @DisplayName("reconcileGttExits full close: qty=1 triggered → closes as CLOSED_TARGET")
    void reconcileGttExits_targetHit_qty1_closesFully() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        // Build position with qty=1
        Position pos = new Position();
        pos.setId(11L);
        pos.setUser(user);
        pos.setSymbol("RELIANCE");
        pos.setSignal(signal);
        pos.setQuantity(1);
        pos.setAvgEntryPrice(BigDecimal.valueOf(2410));
        pos.setGttOrderId("GTT789");
        pos.setStatus(PositionStatus.ACTIVE);
        pos.setOpenedAt(java.time.LocalDateTime.now());
        GttStatusResult triggered = new GttStatusResult(true, BigDecimal.valueOf(2605));

        when(db.getActivePositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getGttStatus("GTT789")).thenReturn(triggered);

        engine.reconcileGttExits();

        // soldQty = max(1, 1/2) = 1, remainingQty = 0 → full close
        verify(db).closePosition(eq(11L), eq(PositionStatus.CLOSED_TARGET), any());
        verify(db, never()).partialExitPosition(any(), anyInt(), any());
        verify(events).publishEvent(any(com.trading.portfolio.events.PositionClosedEvent.class));
    }

    @Test
    @DisplayName("reconcileGttExits skips positions without gttOrderId")
    void reconcileGttExits_noGttId_skipped() {
        Position pos = buildPosition(10L, user, "RELIANCE", "ORD123");
        pos.setGttOrderId(null); // no GTT
        pos.setStatus(PositionStatus.ACTIVE);

        when(db.getActivePositions()).thenReturn(List.of(pos));

        engine.reconcileGttExits();

        verify(broker, never()).getGttStatus(anyString());
    }

    // ── manualExit ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("manualExit cancels GTT, places market sell, closes position")
    void manualExit_activePosition_closesManually() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));

        when(db.getActivePositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.placeMarketSellOrder(eq("RELIANCE"), anyInt(), anyString())).thenReturn("sellOrder789");

        engine.manualExit(10L);

        verify(broker).cancelGttOrder("GTT456");
        verify(broker).placeMarketSellOrder(eq("RELIANCE"), anyInt(), anyString());
        verify(db).recordManualExitOrder(10L, "sellOrder789");
        verify(db).closePosition(10L, PositionStatus.CLOSED_MANUAL, null);
        verify(events).publishEvent(any(com.trading.portfolio.events.PositionClosedEvent.class));
    }

    @Test
    @DisplayName("manualExit propagates exception and leaves DB position ACTIVE when sell order fails")
    void manualExit_sellOrderFails_propagatesExceptionPositionStaysActive() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));

        when(db.getActivePositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.placeMarketSellOrder(eq("RELIANCE"), anyInt(), anyString()))
                .thenThrow(new BrokerOrderException("Insufficient holdings"));

        // Broker failure must propagate — DB position must NOT be closed
        assertThatThrownBy(() -> engine.manualExit(10L))
                .isInstanceOf(BrokerOrderException.class)
                .hasMessageContaining("Insufficient holdings");

        verify(db, never()).closePosition(any(), any(), any());
        verify(db, never()).recordManualExitOrder(any(), any());
        verify(events, never()).publishEvent(any(com.trading.portfolio.events.PositionClosedEvent.class));
    }

    @Test
    @DisplayName("manualExit throws when position not found")
    void manualExit_positionNotFound_throws() {
        when(db.getActivePositions()).thenReturn(List.of());

        assertThatThrownBy(() -> engine.manualExit(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── confirmManualFill ────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmManualFill activates position with admin-supplied qty/price, places GTT")
    void confirmManualFill_activatesPositionWithSuppliedFillData() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildPosition(10L, user, "RELIANCE", "ORD123");
        pos.setSignal(signal);

        when(db.getPendingEntryPositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.placeGttTargetOrder(anyString(), anyInt(), any(), anyString())).thenReturn("GTT456");

        engine.confirmManualFill(10L, 4, BigDecimal.valueOf(410.50));

        verify(broker, never()).getOrderDetail(anyString()); // never asks Zerodha — caller already verified
        verify(db).activatePosition(10L, 4, BigDecimal.valueOf(410.50), "GTT456");
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderFilledEvent.class));
    }

    @Test
    @DisplayName("confirmManualFill still activates position when GTT placement fails with a token/permission error")
    void confirmManualFill_gttPlacementFailsWithBrokerTokenException_stillActivatesPosition() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildPosition(10L, user, "RELIANCE", "ORD123");
        pos.setSignal(signal);

        when(db.getPendingEntryPositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.placeGttTargetOrder(anyString(), anyInt(), any(), anyString()))
                .thenThrow(new BrokerTokenException("Insufficient permission for that call."));

        engine.confirmManualFill(10L, 4, BigDecimal.valueOf(410.50));

        verify(db).activatePosition(10L, 4, BigDecimal.valueOf(410.50), null);
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderFilledEvent.class));
    }

    @Test
    @DisplayName("confirmManualFill throws when position is not PENDING_ENTRY")
    void confirmManualFill_positionNotPending_throws() {
        when(db.getPendingEntryPositions()).thenReturn(List.of());

        assertThatThrownBy(() -> engine.confirmManualFill(99L, 4, BigDecimal.valueOf(410.50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── checkClosingBasisStopLoss ─────────────────────────────────────────────

    @Test
    @DisplayName("checkClosingBasisStopLoss triggers market sell when LTP is below stop-loss")
    void checkClosingBasisStopLoss_ltpBelowSl_placesMarketSell() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));

        when(db.getActivePositionsByBasis(StopLossBasis.DAILY)).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2250))); // below SL 2300
        when(broker.placeMarketSellOrder(eq("RELIANCE"), anyInt(), anyString())).thenReturn("SELL999");

        engine.checkClosingBasisStopLoss(StopLossBasis.DAILY);

        verify(broker).cancelGttOrder("GTT456");
        verify(broker).placeMarketSellOrder(eq("RELIANCE"), eq(4), anyString());
        verify(db).recordManualExitOrder(10L, "SELL999");
        verify(db).closePosition(eq(10L), eq(PositionStatus.CLOSED_SL), any());
        verify(events).publishEvent(any(com.trading.portfolio.events.PositionClosedEvent.class));
    }

    @Test
    @DisplayName("checkClosingBasisStopLoss does not sell when LTP is above stop-loss")
    void checkClosingBasisStopLoss_ltpAboveSl_doesNothing() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));

        when(db.getActivePositionsByBasis(StopLossBasis.DAILY)).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2350))); // above SL 2300

        engine.checkClosingBasisStopLoss(StopLossBasis.DAILY);

        verify(broker, never()).placeMarketSellOrder(anyString(), anyInt(), anyString());
        verify(db, never()).closePosition(any(), any(), any());
    }

    @Test
    @DisplayName("checkClosingBasisStopLoss skips user when token expired")
    void checkClosingBasisStopLoss_tokenExpired_skipsUser() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));

        when(db.getActivePositionsByBasis(StopLossBasis.HOURLY)).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenThrow(new BrokerTokenException("expired"));

        engine.checkClosingBasisStopLoss(StopLossBasis.HOURLY);

        verify(db, never()).closePosition(any(), any(), any());
    }

    @Test
    @DisplayName("checkClosingBasisStopLoss returns early when no active positions")
    void checkClosingBasisStopLoss_noPositions_returnsEarly() {
        when(db.getActivePositionsByBasis(StopLossBasis.WEEKLY)).thenReturn(List.of());

        engine.checkClosingBasisStopLoss(StopLossBasis.WEEKLY);

        verify(db, never()).getUserConfigByUserId(anyLong());
    }

    @Test
    @DisplayName("checkClosingBasisStopLoss uses breakeven SL when set, ignoring signal SL")
    void checkClosingBasisStopLoss_breakevenSlSet_usesBreakevenNotSignalSl() {
        // Signal SL = 2300, breakeven SL = 2410 (avg entry). LTP = 2380: above signal SL but below breakeven.
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, null, BigDecimal.valueOf(2410));
        pos.setBreakevenSl(BigDecimal.valueOf(2410)); // breakeven SL set after partial exit

        when(db.getActivePositionsByBasis(StopLossBasis.DAILY)).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2380))); // 2380 < breakeven 2410
        when(broker.placeMarketSellOrder(eq("RELIANCE"), anyInt(), anyString())).thenReturn("SELL_BREAKEVEN");

        engine.checkClosingBasisStopLoss(StopLossBasis.DAILY);

        // Should sell because LTP < breakevenSl, even though LTP > signal.stopLoss
        verify(broker).placeMarketSellOrder(eq("RELIANCE"), eq(4), anyString());
        verify(db).closePosition(eq(10L), eq(PositionStatus.CLOSED_SL), any());
    }

    @Test
    @DisplayName("checkClosingBasisStopLoss skips SL check (not the whole user) when quotes fetch is permission-denied")
    void checkClosingBasisStopLoss_quotesPermissionDenied_skipsSlCheckOnly() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));

        when(db.getActivePositionsByBasis(StopLossBasis.DAILY)).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getQuotes(any())).thenThrow(new BrokerTokenException("Insufficient permission for that call."));

        engine.checkClosingBasisStopLoss(StopLossBasis.DAILY);

        verify(broker, never()).placeMarketSellOrder(anyString(), anyInt(), anyString());
        verify(db, never()).closePosition(any(), any(), any());
    }

    // ── previewOrderForSignal ─────────────────────────────────────────────────

    @Test
    @DisplayName("previewOrder throws when signal not found")
    void previewOrder_signalNotFound_throws() {
        when(db.getSignalById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.previewOrderForSignal(userConfig, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("previewOrder blocks when signal is not ACTIVE")
    void previewOrder_signalNotActive_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        signal.setStatus(SignalStatus.CANCELLED);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));

        OrderPreviewResponse preview = engine.previewOrderForSignal(userConfig, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("active");
    }

    @Test
    @DisplayName("previewOrder blocks when trading is paused")
    void previewOrder_tradingPaused_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        UserConfig paused = UserConfig.builder()
                .user(user).maxPositions(5)
                .positionSizingMethod(PositionSizingMethod.FIXED)
                .positionSizingValue(BigDecimal.valueOf(10_000))
                .orderExpiryDays(5)
                .tradingPaused(true)
                .build();

        OrderPreviewResponse preview = engine.previewOrderForSignal(paused, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("paused");
    }

    @Test
    @DisplayName("previewOrder blocks when user already has a position for this signal")
    void previewOrder_duplicatePosition_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(true);

        OrderPreviewResponse preview = engine.previewOrderForSignal(userConfig, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("already");
    }

    @Test
    @DisplayName("previewOrder blocks when symbol is already held")
    void previewOrder_symbolAlreadyHeld_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of("RELIANCE"));

        OrderPreviewResponse preview = engine.previewOrderForSignal(userConfig, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("RELIANCE");
    }

    @Test
    @DisplayName("previewOrder blocks when no free position slots remain")
    void previewOrder_noFreeSlots_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(5L); // maxPositions = 5

        OrderPreviewResponse preview = engine.previewOrderForSignal(userConfig, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("slot");
    }

    @Test
    @DisplayName("previewOrder blocks when Zerodha account not connected")
    void previewOrder_zerodhaNotConnected_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        // zerodhaConnected defaults to false in userConfig

        OrderPreviewResponse preview = engine.previewOrderForSignal(userConfig, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("connected");
    }

    @Test
    @DisplayName("previewOrder blocks when Zerodha session token is expired")
    void previewOrder_tokenExpired_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenThrow(new BrokerTokenException("expired"));

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("expired");
    }

    @Test
    @DisplayName("previewOrder blocks when margin fetch fails due to network error")
    void previewOrder_marginFetchNetworkError_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenThrow(new BrokerNetworkException("timeout"));

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("margin");
    }

    @Test
    @DisplayName("previewOrder blocks when margin is insufficient for even 1 share")
    void previewOrder_insufficientMargin_blocksWithReason() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100));
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2390)));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(0);

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        assertThat(preview.canPlace()).isFalse();
        assertThat(preview.reason()).containsIgnoringCase("margin");
    }

    @Test
    @DisplayName("previewOrder sets warning when LTP is above entry price but keeps canPlace=true")
    void previewOrder_ltpAboveEntry_canPlaceTrueWithWarning() {
        // entry=168, sl=160, ltp=174 — the real-world bug-report scenario
        Signal signal = buildSignal(1L, "EXAMPLE", 168, 160, 185);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(broker.getQuotes(any())).thenReturn(Map.of("EXAMPLE", BigDecimal.valueOf(174)));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(5);

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        assertThat(preview.canPlace()).isTrue();
        assertThat(preview.reason()).isNull();
        assertThat(preview.warning()).isNotNull();
        assertThat(preview.warning()).contains("174");
        assertThat(preview.warning()).contains("168");
    }

    @Test
    @DisplayName("previewOrder sets no warning when LTP equals entry price (boundary)")
    void previewOrder_ltpExactlyAtEntry_canPlaceTrueNoWarning() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2400)));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        assertThat(preview.canPlace()).isTrue();
        assertThat(preview.warning()).isNull();
    }

    @Test
    @DisplayName("previewOrder sets no warning when LTP is below entry price")
    void previewOrder_ltpBelowEntry_canPlaceTrueNoWarning() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2350)));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        assertThat(preview.canPlace()).isTrue();
        assertThat(preview.warning()).isNull();
    }

    @Test
    @DisplayName("previewOrder skips warning gracefully when LTP fetch fails (network error)")
    void previewOrder_ltpFetchNetworkError_canPlaceTrueNoWarning() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(broker.getQuotes(any())).thenThrow(new BrokerNetworkException("timeout"));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        // Network failure on LTP fetch must not block the preview
        assertThat(preview.canPlace()).isTrue();
        assertThat(preview.warning()).isNull();
    }

    @Test
    @DisplayName("previewOrder skips warning gracefully when LTP fetch is denied by broker (permission error)")
    void previewOrder_ltpFetchPermissionDenied_canPlaceTrueNoWarning() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(broker.getQuotes(any())).thenThrow(new BrokerTokenException("Insufficient permission for that call."));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        // A Zerodha PermissionException on the LTP fetch must not fail the whole preview with a 401
        assertThat(preview.canPlace()).isTrue();
        assertThat(preview.warning()).isNull();
    }

    @Test
    @DisplayName("previewOrder returns correct quantity and estimated cost on happy path")
    void previewOrder_happyPath_returnsCorrectQtyAndCost() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        UserConfig connected = connectedConfig();
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(2L); // 3 slots free
        when(brokerAdapterFactory.forUser(connected)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(broker.getQuotes(any())).thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2380)));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);

        OrderPreviewResponse preview = engine.previewOrderForSignal(connected, 1L);

        assertThat(preview.canPlace()).isTrue();
        assertThat(preview.reason()).isNull();
        assertThat(preview.warning()).isNull();
        assertThat(preview.estimatedQty()).isEqualTo(4);
        // cost = entryPrice × qty = 2400 × 4 = 9600
        assertThat(preview.estimatedCost()).isEqualByComparingTo(BigDecimal.valueOf(9600));
        assertThat(preview.availableSlots()).isEqualTo(3);
    }

    // ── placeOrderForSignal ───────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder throws when trading is paused")
    void placeOrder_tradingPaused_throws() {
        UserConfig paused = UserConfig.builder()
                .user(user).maxPositions(5)
                .positionSizingMethod(PositionSizingMethod.FIXED)
                .positionSizingValue(BigDecimal.valueOf(10_000))
                .orderExpiryDays(5)
                .tradingPaused(true)
                .build();

        assertThatThrownBy(() -> engine.placeOrderForSignal(paused, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paused");
    }

    @Test
    @DisplayName("placeOrder throws when signal not found")
    void placeOrder_signalNotFound_throws() {
        when(db.getSignalById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.placeOrderForSignal(userConfig, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("placeOrder throws when signal is not ACTIVE")
    void placeOrder_signalNotActive_throws() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        signal.setStatus(SignalStatus.CANCELLED);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));

        assertThatThrownBy(() -> engine.placeOrderForSignal(userConfig, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("placeOrder throws when user already has position for this signal")
    void placeOrder_duplicatePosition_throws() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> engine.placeOrderForSignal(userConfig, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");
    }

    @Test
    @DisplayName("placeOrder throws when symbol is already held by user")
    void placeOrder_symbolAlreadyHeld_throws() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of("RELIANCE"));

        assertThatThrownBy(() -> engine.placeOrderForSignal(userConfig, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELIANCE");
    }

    @Test
    @DisplayName("placeOrder throws when no free position slots remain")
    void placeOrder_noFreeSlots_throws() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(5L); // maxPositions=5

        assertThatThrownBy(() -> engine.placeOrderForSignal(userConfig, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slot");
    }

    @Test
    @DisplayName("placeOrder throws when margin is insufficient (qty=0)")
    void placeOrder_insufficientMargin_throws() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(50));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> engine.placeOrderForSignal(userConfig, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("placeOrder succeeds: creates position, places limit order, records it, fires event")
    void placeOrder_brokerOrderSucceeds_returnsPositionId() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);
        when(db.createPendingPosition(any(), any(), anyInt())).thenReturn(20L);
        when(broker.placeLimitOrder(anyString(), anyInt(), any(), anyString())).thenReturn("ORD999");

        Long positionId = engine.placeOrderForSignal(userConfig, 1L);

        assertThat(positionId).isEqualTo(20L);
        verify(broker).placeLimitOrder(eq("RELIANCE"), eq(4), any(BigDecimal.class), eq("pos_20"));
        verify(db).recordEntryOrder(20L, userConfig, signal, "ORD999", 4);
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("placeOrder rolls back pending position when broker limit order fails")
    void placeOrder_brokerOrderFails_rollsBackPosition() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(4);
        when(db.createPendingPosition(any(), any(), anyInt())).thenReturn(20L);
        when(broker.placeLimitOrder(anyString(), anyInt(), any(), anyString()))
                .thenThrow(new BrokerOrderException("Rejected by exchange"));

        assertThatThrownBy(() -> engine.placeOrderForSignal(userConfig, 1L))
                .isInstanceOf(BrokerOrderException.class);

        verify(db).cancelPosition(20L);
        verify(db, never()).recordEntryOrder(any(), any(), any(), any(), anyInt());
        verify(events, never()).publishEvent(any(com.trading.portfolio.events.OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("placeOrder proceeds and places order even when LTP is above entry (user override)")
    void placeOrder_ltpAboveEntry_orderStillPlaced() {
        // No LTP guard in placeOrderForSignal — user chose to override the warning
        Signal signal = buildSignal(1L, "EXAMPLE", 168, 160, 185);
        when(db.getSignalById(1L)).thenReturn(Optional.of(signal));
        when(db.hasActivePosition(1L, 1L)).thenReturn(false);
        when(db.getOccupiedSymbols(1L)).thenReturn(Set.of());
        when(db.countActivePositions(1L)).thenReturn(0L);
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getAvailableMargin()).thenReturn(BigDecimal.valueOf(100_000));
        when(sizingService.calculate(any(), any(), any(), any())).thenReturn(5);
        when(db.createPendingPosition(any(), any(), anyInt())).thenReturn(21L);
        when(broker.placeLimitOrder(anyString(), anyInt(), any(), anyString())).thenReturn("ORD_OVERRIDE");

        Long positionId = engine.placeOrderForSignal(userConfig, 1L);

        // Must proceed — limit order at entry price (168), not at current LTP (174)
        assertThat(positionId).isEqualTo(21L);
        verify(broker).placeLimitOrder(eq("EXAMPLE"), eq(5), any(BigDecimal.class), eq("pos_21"));
        verify(events).publishEvent(any(com.trading.portfolio.events.OrderPlacedEvent.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** UserConfig with Zerodha connected, needed for broker-reaching test paths. */
    private UserConfig connectedConfig() {
        return UserConfig.builder()
                .user(user)
                .maxPositions(5)
                .positionSizingMethod(PositionSizingMethod.FIXED)
                .positionSizingValue(BigDecimal.valueOf(10_000))
                .orderExpiryDays(5)
                .zerodhaConnected(true)
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Signal buildSignal(long id, String symbol, double entry, double sl, double target) {
        Signal s = new Signal();
        s.setId(id);
        s.setSymbol(symbol);
        s.setEntryPrice(BigDecimal.valueOf(entry));
        s.setStopLoss(BigDecimal.valueOf(sl));
        s.setTarget(BigDecimal.valueOf(target));
        BigDecimal risk   = BigDecimal.valueOf(entry - sl);
        BigDecimal reward = BigDecimal.valueOf(target - entry);
        s.setRiskRewardRatio(reward.divide(risk, 4, java.math.RoundingMode.HALF_UP));
        return s;
    }

    private Position buildPosition(long id, User owner, String symbol, String orderId) {
        Position p = new Position();
        p.setId(id);
        p.setUser(owner);
        p.setSymbol(symbol);
        p.setQuantity(4);
        p.setEntryOrderId(orderId);
        p.setStatus(PositionStatus.PENDING_ENTRY);
        return p;
    }

    private Position buildActivePosition(long id, User owner, String symbol, Signal signal,
                                         String gttId, BigDecimal avgEntry) {
        Position p = new Position();
        p.setId(id);
        p.setUser(owner);
        p.setSymbol(symbol);
        p.setSignal(signal);
        p.setQuantity(4);
        p.setAvgEntryPrice(avgEntry);
        p.setGttOrderId(gttId);
        p.setStatus(PositionStatus.ACTIVE);
        p.setOpenedAt(LocalDateTime.now());
        return p;
    }
}
