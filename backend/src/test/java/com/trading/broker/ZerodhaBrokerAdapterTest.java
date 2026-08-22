package com.trading.broker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZerodhaBrokerAdapterTest {

    @Mock ZerodhaApiClient apiClient;
    private ZerodhaBrokerAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ZerodhaBrokerAdapter(apiClient);
    }

    // ── placeLimitOrder ───────────────────────────────────────────────────────

    @Test
    @DisplayName("placeLimitOrder delegates to apiClient and returns order ID")
    void placeLimitOrder_delegatesAndReturnsOrderId() {
        when(apiClient.placeLimitOrder("RELIANCE", 10, new BigDecimal("100"), "pos_1"))
                .thenReturn("order123");

        String orderId = adapter.placeLimitOrder("RELIANCE", 10, new BigDecimal("100"), "pos_1");

        assertThat(orderId).isEqualTo("order123");
        verify(apiClient).placeLimitOrder("RELIANCE", 10, new BigDecimal("100"), "pos_1");
    }

    // ── placeGttOcoOrder ──────────────────────────────────────────────────────

    @Test
    @DisplayName("placeGttOcoOrder fetches LTP then places GTT with lastPrice")
    void placeGttOcoOrder_fetchesLtpThenPlacesGtt() {
        when(apiClient.getQuotes(List.of("TCS")))
                .thenReturn(Map.of("TCS", new BigDecimal("3500")));
        when(apiClient.placeGttOcoOrder(eq("TCS"), eq(5),
                eq(new BigDecimal("3200")), eq(new BigDecimal("3900")),
                eq(new BigDecimal("3500")), eq("pos_2")))
                .thenReturn("gtt456");

        String gttId = adapter.placeGttOcoOrder("TCS", 5,
                new BigDecimal("3200"), new BigDecimal("3900"), "pos_2");

        assertThat(gttId).isEqualTo("gtt456");
        verify(apiClient).getQuotes(List.of("TCS"));
        verify(apiClient).placeGttOcoOrder("TCS", 5,
                new BigDecimal("3200"), new BigDecimal("3900"),
                new BigDecimal("3500"), "pos_2");
    }

    @Test
    @DisplayName("placeGttOcoOrder throws BrokerOrderException when LTP unavailable")
    void placeGttOcoOrder_ltpUnavailable_throws() {
        when(apiClient.getQuotes(List.of("UNKNOWN"))).thenReturn(Map.of());

        assertThatThrownBy(() -> adapter.placeGttOcoOrder(
                "UNKNOWN", 5, new BigDecimal("90"), new BigDecimal("120"), "pos_3"))
                .isInstanceOf(BrokerOrderException.class)
                .hasMessageContaining("LTP");
    }

    // ── cancelOrder ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder delegates to apiClient")
    void cancelOrder_delegates() {
        adapter.cancelOrder("order123");
        verify(apiClient).cancelOrder("order123");
    }

    // ── cancelGttOrder ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelGttOrder delegates to apiClient")
    void cancelGttOrder_delegates() {
        adapter.cancelGttOrder("gtt456");
        verify(apiClient).cancelGttOrder("gtt456");
    }

    // ── getOrderStatus ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderStatus returns COMPLETE when broker reports COMPLETE")
    void getOrderStatus_returnsStatus() {
        when(apiClient.getOrderStatus("order123")).thenReturn(BrokerOrderStatus.COMPLETE);

        assertThat(adapter.getOrderStatus("order123")).isEqualTo(BrokerOrderStatus.COMPLETE);
    }

    // ── getHoldings ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHoldings delegates and returns list")
    void getHoldings_delegatesAndReturns() {
        List<Holding> holdings = List.of(
                new Holding("RELIANCE", 10, new BigDecimal("2400"), new BigDecimal("2500")));
        when(apiClient.getHoldings()).thenReturn(holdings);

        assertThat(adapter.getHoldings()).hasSize(1)
                .first().extracting(Holding::symbol).isEqualTo("RELIANCE");
    }

    // ── getAvailableMargin ────────────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableMargin delegates and returns value")
    void getAvailableMargin_delegates() {
        when(apiClient.getAvailableMargin()).thenReturn(new BigDecimal("50000"));

        assertThat(adapter.getAvailableMargin()).isEqualByComparingTo("50000");
    }

    // ── getQuotes ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getQuotes delegates and returns map")
    void getQuotes_delegates() {
        Map<String, BigDecimal> quotes = Map.of("RELIANCE", new BigDecimal("2500"));
        when(apiClient.getQuotes(List.of("RELIANCE"))).thenReturn(quotes);

        assertThat(adapter.getQuotes(List.of("RELIANCE"))).containsEntry("RELIANCE", new BigDecimal("2500"));
    }

    // ── BrokerTokenException propagates ──────────────────────────────────────

    @Test
    @DisplayName("BrokerTokenException from apiClient propagates without wrapping")
    void brokerTokenException_propagatesDirectly() {
        when(apiClient.getAvailableMargin()).thenThrow(new BrokerTokenException("Token expired"));

        assertThatThrownBy(() -> adapter.getAvailableMargin())
                .isInstanceOf(BrokerTokenException.class)
                .hasMessage("Token expired");
    }
}
