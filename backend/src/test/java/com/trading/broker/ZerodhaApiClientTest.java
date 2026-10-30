package com.trading.broker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ZerodhaApiClientTest {

    private static final BigDecimal TICK = new BigDecimal("0.05");

    @ParameterizedTest
    @CsvSource({
            "795.25, 787.25",   // 787.2975 -> rounded down to the 0.05 tick
            "100.00, 99.00",    // already on a tick
            "123.45, 122.20",   // 122.2155 -> 122.20
            "10.00, 9.90"
    })
    @DisplayName("protectedSellPrice is 1% below LTP rounded down to a 0.05 tick")
    void protectedSellPrice_roundsDownToTick(String ltp, String expected) {
        BigDecimal price = ZerodhaApiClient.protectedSellPrice(new BigDecimal(ltp));

        assertThat(price).isEqualByComparingTo(expected);
        assertThat(price.remainder(TICK)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
