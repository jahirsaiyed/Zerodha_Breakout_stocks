package com.trading.portfolio.events;

public record OrderExpiredEvent(Long positionId, String symbol, String zerodhaOrderId) {}
