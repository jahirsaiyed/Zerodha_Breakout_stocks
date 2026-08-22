package com.trading.portfolio.events;

public record OrderExpiredEvent(Long positionId, String zerodhaOrderId) {}
