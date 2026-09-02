package com.trading.portfolio.events;

public record OrderLookupFailedEvent(Long positionId, String symbol, String zerodhaOrderId) {}
