package com.trading.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFinancePriceServiceTest {

    private final GoogleFinancePriceService service = new GoogleFinancePriceService();

    /**
     * Builds minimal HTML that satisfies the CSS selector used by GoogleFinancePriceService.
     * Selector: div.N6SYTe span[jsname=Pdsbrc]
     */
    private static String buildHtml(String priceText) {
        return """
                <html><body>
                  <div class="ujg0He">
                    <div class="N6SYTe"><span jsname="Pdsbrc"><span>%s</span></span></div>
                  </div>
                </body></html>
                """.formatted(priceText);
    }

    @Test
    @DisplayName("parsePrice extracts integer price via Jsoup selector")
    void parsePrice_integer() {
        assertThat(service.parsePrice(buildHtml("2,450")))
                .isEqualByComparingTo(new BigDecimal("2450"));
    }

    @Test
    @DisplayName("parsePrice extracts decimal price via Jsoup selector")
    void parsePrice_decimal() {
        assertThat(service.parsePrice(buildHtml("2,450.75")))
                .isEqualByComparingTo(new BigDecimal("2450.75"));
    }

    @Test
    @DisplayName("parsePrice extracts small decimal price via Jsoup selector")
    void parsePrice_smallDecimal() {
        assertThat(service.parsePrice(buildHtml("55.82")))
                .isEqualByComparingTo(new BigDecimal("55.82"));
    }

    @Test
    @DisplayName("parsePrice returns null when selector finds nothing")
    void parsePrice_absent_returnsNull() {
        assertThat(service.parsePrice("<html><body>No price here</body></html>")).isNull();
    }

    @Test
    @DisplayName("getPrices returns empty map for empty symbol list")
    void getPrices_emptyList_returnsEmptyMap() {
        assertThat(service.getPrices(java.util.List.of())).isEmpty();
    }
}
