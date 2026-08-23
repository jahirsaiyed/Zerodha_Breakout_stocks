package com.trading.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFinancePriceServiceTest {

    private final GoogleFinancePriceService service = new GoogleFinancePriceService();

    /**
     * Builds minimal HTML that satisfies the CSS selector path used by GoogleFinancePriceService.
     * Selector: body > c-wiz:nth-of-type(3) > div > div > div > div:nth-of-type(2) >
     *           div:nth-of-type(2) > div > div > c-wiz > div > div:nth-of-type(3) >
     *           c-wiz > div > div > div:nth-of-type(1) > div > div:nth-of-type(2) >
     *           div > div:nth-of-type(1) > div:nth-of-type(1) > span > span
     */
    private static String buildHtml(String priceText) {
        return """
                <html><body>
                  <c-wiz></c-wiz>
                  <c-wiz></c-wiz>
                  <c-wiz>
                    <div><div><div>
                      <div></div>
                      <div>
                        <div></div>
                        <div>
                          <div><div>
                            <c-wiz>
                              <div>
                                <div></div>
                                <div></div>
                                <div>
                                  <c-wiz>
                                    <div><div>
                                      <div>
                                        <div>
                                          <div></div>
                                          <div>
                                            <div>
                                              <div>
                                                <div><span><span>%s</span></span></div>
                                              </div>
                                            </div>
                                          </div>
                                        </div>
                                      </div>
                                    </div></div>
                                  </c-wiz>
                                </div>
                              </div>
                            </c-wiz>
                          </div></div>
                        </div>
                      </div>
                    </div></div></div>
                  </c-wiz>
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
