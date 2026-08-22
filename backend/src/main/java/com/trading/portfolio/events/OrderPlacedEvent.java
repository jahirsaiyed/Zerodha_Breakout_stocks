package com.trading.portfolio.events;

public record OrderPlacedEvent(Long positionId, String symbol, String zerodhaOrderId) {}
