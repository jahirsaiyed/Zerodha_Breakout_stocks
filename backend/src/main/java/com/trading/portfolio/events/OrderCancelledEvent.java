package com.trading.portfolio.events;

public record OrderCancelledEvent(Long positionId, String symbol, String zerodhaOrderId, String reason) {}
