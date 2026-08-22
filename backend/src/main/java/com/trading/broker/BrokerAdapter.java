package com.trading.broker;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Broker-agnostic trading interface. Each user gets their own instance
 * (created by {@link BrokerAdapterFactory}) with their own credentials.
 *
 * <p>Implementations must handle:
 * <ul>
 *   <li>{@link BrokerNetworkException} — transient, callers may retry</li>
 *   <li>{@link BrokerTokenException} — expired token, user must re-authenticate</li>
 *   <li>{@link BrokerOrderException} — order rejected, caller logs and alerts</li>
 * </ul>
 */
public interface BrokerAdapter {

    /**
     * Places a CNC limit buy order for the given symbol.
     * @return broker-assigned order ID
     */
    String placeLimitOrder(String symbol, int quantity, BigDecimal price, String tag);

    /**
     * Places a two-leg GTT OCO order (stop-loss + target) for a held position.
     * @return broker-assigned GTT trigger ID
     */
    String placeGttOcoOrder(String symbol, int quantity, BigDecimal stopLoss, BigDecimal target, String tag);

    /** Cancels a regular order by order ID. */
    void cancelOrder(String orderId);

    /** Cancels a GTT trigger by trigger ID. */
    void cancelGttOrder(String gttId);

    /** Returns the current status of a regular order. */
    BrokerOrderStatus getOrderStatus(String orderId);

    /** Returns all holdings (long positions) in the user's demat account. */
    List<Holding> getHoldings();

    /** Returns available cash margin for the equity segment. */
    BigDecimal getAvailableMargin();

    /**
     * Returns last traded prices for the given symbols.
     * @param symbols NSE trading symbols (e.g. "RELIANCE", "TCS")
     * @return map of symbol → last price; absent if quote unavailable
     */
    Map<String, BigDecimal> getQuotes(List<String> symbols);

    /**
     * Exchanges a Zerodha request token (obtained after web login) for an access token.
     * @return new access token (store encrypted in user_configs)
     */
    String refreshAccessToken(String apiKey, String apiSecret, String requestToken);
}
