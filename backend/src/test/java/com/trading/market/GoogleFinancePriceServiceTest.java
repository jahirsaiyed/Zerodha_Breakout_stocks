package com.trading.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFinancePriceServiceTest {

    private final GoogleFinancePriceService service = new GoogleFinancePriceService();

    // Google Finance embeds price in AF_initDataCallback as [...,"INR",[<price>,<chg>,<chgPct>,2,2,...],...]
    // The trailing ,2,2 pattern distinguishes the price tuple from other numeric arrays.

    @Test
    @DisplayName("parsePrice extracts integer price from AF_initDataCallback array")
    void parsePrice_plainInteger() {
        String html = "\"INR\",[2450,-10.5,-0.43,2,2,2],null,2460";
        assertThat(service.parsePrice(html)).isEqualByComparingTo(new BigDecimal("2450"));
    }

    @Test
    @DisplayName("parsePrice extracts decimal price from AF_initDataCallback array")
    void parsePrice_decimal() {
        String html = "\"INR\",[2450.75,-5.25,-0.21,2,2,2],null,2456";
        assertThat(service.parsePrice(html)).isEqualByComparingTo(new BigDecimal("2450.75"));
    }

    @Test
    @DisplayName("parsePrice handles negative daily change")
    void parsePrice_negativeChange() {
        // Realistic snippet from KHAICHEM page: price=55.82, change=-0.83, changePct=-1.46
        String html = "\"INR\",[55.82,-0.83000183,-1.46514,2,2,2],null,56.65";
        assertThat(service.parsePrice(html)).isEqualByComparingTo(new BigDecimal("55.82"));
    }

    @Test
    @DisplayName("parsePrice returns null when AF_initDataCallback pattern is absent")
    void parsePrice_absent_returnsNull() {
        String html = "<html><body>No price here</body></html>";
        assertThat(service.parsePrice(html)).isNull();
    }

    @Test
    @DisplayName("getPrices returns empty map for empty symbol list")
    void getPrices_emptyList_returnsEmptyMap() {
        assertThat(service.getPrices(java.util.List.of())).isEmpty();
    }
}
