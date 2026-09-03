package com.trading.broker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * {@link BrokerAdapter} implementation backed by the Zerodha Kite Connect API.
 * One instance per user; created by {@link BrokerAdapterFactory}.
 */
@Slf4j
@RequiredArgsConstructor
public class ZerodhaBrokerAdapter implements BrokerAdapter {

    private final ZerodhaApiClient apiClient;

    @Override
    public String placeLimitOrder(String symbol, int quantity, BigDecimal price, String tag) {
        log.debug("Placing limit order: symbol={} qty={} price={} tag={}", symbol, quantity, price, tag);
        return apiClient.placeLimitOrder(symbol, quantity, price, tag);
    }

    @Override
    public String placeMarketSellOrder(String symbol, int quantity, String tag) {
        log.debug("Placing market sell: symbol={} qty={} tag={}", symbol, quantity, tag);
        return apiClient.placeMarketSellOrder(symbol, quantity, tag);
    }

    @Override
    public String placeGttOcoOrder(String symbol, int quantity,
                                   BigDecimal stopLoss, BigDecimal target, String tag) {
        // Zerodha GTT requires the current last price in the condition
        BigDecimal lastPrice = fetchLastPrice(symbol);
        log.debug("Placing GTT OCO: symbol={} qty={} sl={} target={} ltp={} tag={}",
                symbol, quantity, stopLoss, target, lastPrice, tag);
        return apiClient.placeGttOcoOrder(symbol, quantity, stopLoss, target, lastPrice, tag);
    }

    @Override
    public String placeGttTargetOrder(String symbol, int quantity, BigDecimal target, String tag) {
        BigDecimal lastPrice = fetchLastPrice(symbol);
        log.debug("Placing GTT target: symbol={} qty={} target={} ltp={} tag={}",
                symbol, quantity, target, lastPrice, tag);
        return apiClient.placeGttTargetOrder(symbol, quantity, target, lastPrice, tag);
    }

    @Override
    public void cancelOrder(String orderId) {
        log.debug("Cancelling order: {}", orderId);
        apiClient.cancelOrder(orderId);
    }

    @Override
    public void cancelGttOrder(String gttId) {
        log.debug("Cancelling GTT: {}", gttId);
        apiClient.cancelGttOrder(gttId);
    }

    @Override
    public BrokerOrderDetail getOrderDetail(String orderId) {
        return apiClient.getOrderDetail(orderId);
    }

    @Override
    public GttStatusResult getGttStatus(String gttId) {
        return apiClient.getGttStatus(gttId);
    }

    @Override
    public List<Holding> getHoldings() {
        return apiClient.getHoldings();
    }

    @Override
    public List<Holding> getDayPositions() {
        return apiClient.getDayPositions();
    }

    @Override
    public BigDecimal getAvailableMargin() {
        return apiClient.getAvailableMargin();
    }

    @Override
    public Map<String, BigDecimal> getQuotes(List<String> symbols) {
        return apiClient.getQuotes(symbols);
    }

    @Override
    public String refreshAccessToken(String apiKey, String apiSecret, String requestToken) {
        return apiClient.refreshAccessToken(apiKey, apiSecret, requestToken);
    }

    private BigDecimal fetchLastPrice(String symbol) {
        Map<String, BigDecimal> quotes = apiClient.getQuotes(List.of(symbol));
        BigDecimal ltp = quotes.get(symbol);
        if (ltp == null) {
            throw new BrokerOrderException("Could not fetch LTP for " + symbol + " — cannot place GTT");
        }
        return ltp;
    }
}
