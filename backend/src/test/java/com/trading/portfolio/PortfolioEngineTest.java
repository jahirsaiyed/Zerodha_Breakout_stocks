package com.trading.portfolio;

import com.trading.broker.*;
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

        verify(broker).placeGttTargetOrder(eq("RELIANCE"), eq(4), any(BigDecimal.class), eq("pos_10"));
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
    @DisplayName("checkOrderFills returns early when no pending positions")
    void checkOrderFills_noPendingPositions_returnsEarly() {
        when(db.getPendingEntryPositions()).thenReturn(List.of());

        engine.checkOrderFills();

        verify(db, never()).getUserConfigByUserId(anyLong());
    }

    // ── reconcileGttExits ────────────────────────────────────────────────────

    @Test
    @DisplayName("reconcileGttExits closes position at target")
    void reconcileGttExits_targetHit_closesWithTargetStatus() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));
        GttStatusResult triggered = new GttStatusResult(true, BigDecimal.valueOf(2605)); // above target

        when(db.getActivePositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getGttStatus("GTT456")).thenReturn(triggered);

        engine.reconcileGttExits();

        ArgumentCaptor<PositionStatus> statusCaptor = ArgumentCaptor.forClass(PositionStatus.class);
        verify(db).closePosition(eq(10L), statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue()).isEqualTo(PositionStatus.CLOSED_TARGET);
    }

    @Test
    @DisplayName("reconcileGttExits closes position at stop-loss")
    void reconcileGttExits_slHit_closesWithSlStatus() {
        Signal signal = buildSignal(1L, "RELIANCE", 2400, 2300, 2600);
        Position pos = buildActivePosition(10L, user, "RELIANCE", signal, "GTT456", BigDecimal.valueOf(2410));
        GttStatusResult triggered = new GttStatusResult(true, BigDecimal.valueOf(2295)); // below sl

        when(db.getActivePositions()).thenReturn(List.of(pos));
        when(db.getUserConfigByUserId(1L)).thenReturn(Optional.of(userConfig));
        when(brokerAdapterFactory.forUser(userConfig)).thenReturn(broker);
        when(broker.getGttStatus("GTT456")).thenReturn(triggered);

        engine.reconcileGttExits();

        ArgumentCaptor<PositionStatus> statusCaptor = ArgumentCaptor.forClass(PositionStatus.class);
        verify(db).closePosition(eq(10L), statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue()).isEqualTo(PositionStatus.CLOSED_SL);
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
