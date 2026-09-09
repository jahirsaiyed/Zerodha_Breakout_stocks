package com.trading.portfolio;

import com.trading.broker.BrokerAdapter;
import com.trading.broker.BrokerAdapterFactory;
import com.trading.broker.BrokerTokenException;
import com.trading.broker.Holding;
import com.trading.users.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LivePriceServiceTest {

    @Mock BrokerAdapterFactory brokerAdapterFactory;
    @InjectMocks LivePriceService livePriceService;

    private UserConfig config;

    @BeforeEach
    void setUp() {
        config = UserConfig.builder().build();
    }

    @Test
    @DisplayName("getLivePrices sources price from holdings")
    void getLivePrices_sourcesFromHoldings() {
        BrokerAdapter broker = mock(BrokerAdapter.class);
        when(brokerAdapterFactory.forUser(config)).thenReturn(broker);
        when(broker.getHoldings()).thenReturn(List.of(
                new Holding("RELIANCE", 29, new BigDecimal("152.18"), new BigDecimal("155.00"))));
        when(broker.getDayPositions()).thenReturn(List.of());

        Map<String, BigDecimal> prices = livePriceService.getLivePrices(config, Set.of("RELIANCE"));

        assertThat(prices).containsEntry("RELIANCE", new BigDecimal("155.00"));
    }

    @Test
    @DisplayName("getLivePrices falls back to day positions for a same-day fill")
    void getLivePrices_fallsBackToDayPositions() {
        BrokerAdapter broker = mock(BrokerAdapter.class);
        when(brokerAdapterFactory.forUser(config)).thenReturn(broker);
        when(broker.getHoldings()).thenReturn(List.of());
        when(broker.getDayPositions()).thenReturn(List.of(
                new Holding("RELIANCE", 4, new BigDecimal("527.35"), new BigDecimal("540.00"))));

        Map<String, BigDecimal> prices = livePriceService.getLivePrices(config, Set.of("RELIANCE"));

        assertThat(prices).containsEntry("RELIANCE", new BigDecimal("540.00"));
    }

    @Test
    @DisplayName("getLivePrices returns empty map when broker token is invalid")
    void getLivePrices_brokerTokenException_returnsEmptyMap() {
        when(brokerAdapterFactory.forUser(config)).thenThrow(new BrokerTokenException("no access token"));

        Map<String, BigDecimal> prices = livePriceService.getLivePrices(config, Set.of("RELIANCE"));

        assertThat(prices).isEmpty();
    }

    @Test
    @DisplayName("getLivePrices returns empty map on unexpected broker failure")
    void getLivePrices_unexpectedException_returnsEmptyMap() {
        when(brokerAdapterFactory.forUser(config)).thenThrow(new RuntimeException("network error"));

        Map<String, BigDecimal> prices = livePriceService.getLivePrices(config, Set.of("RELIANCE"));

        assertThat(prices).isEmpty();
    }

    @Test
    @DisplayName("getLivePrices returns empty map for empty symbol set without calling the broker")
    void getLivePrices_emptySymbols_skipsBrokerCall() {
        Map<String, BigDecimal> prices = livePriceService.getLivePrices(config, Set.of());

        assertThat(prices).isEmpty();
        verifyNoInteractions(brokerAdapterFactory);
    }
}
