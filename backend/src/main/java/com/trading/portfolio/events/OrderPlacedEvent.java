package com.trading.portfolio.events;

public record OrderPlacedEvent(Long positionId, String zerodhaOrderId) {}
