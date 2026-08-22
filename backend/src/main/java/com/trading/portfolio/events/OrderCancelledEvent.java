package com.trading.portfolio.events;

public record OrderCancelledEvent(Long positionId, String zerodhaOrderId, String reason) {}
