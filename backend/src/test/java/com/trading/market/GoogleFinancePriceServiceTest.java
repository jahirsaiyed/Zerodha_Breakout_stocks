package com.trading.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFinancePriceServiceTest {

    private final GoogleFinancePriceService service = new GoogleFinancePriceService();

    @Test
    @DisplayName("parsePrice extracts plain integer price")
    void parsePrice_plainInteger() {
        String html = "<div data-last-price=\"2450\" class=\"YMlKec\">₹2,450</div>";
        assertThat(service.parsePrice(html)).isEqualByComparingTo(new BigDecimal("2450"));
    }

    @Test
    @DisplayName("parsePrice extracts decimal price")
    void parsePrice_decimal() {
        String html = "data-last-price=\"2450.75\" data-prev-close=\"2400\"";
        assertThat(service.parsePrice(html)).isEqualByComparingTo(new BigDecimal("2450.75"));
    }

    @Test
    @DisplayName("parsePrice strips commas from large numbers")
    void parsePrice_withCommas() {
        String html = "data-last-price=\"1,234,567.89\"";
        assertThat(service.parsePrice(html)).isEqualByComparingTo(new BigDecimal("1234567.89"));
    }

    @Test
    @DisplayName("parsePrice returns null when attribute is absent")
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
