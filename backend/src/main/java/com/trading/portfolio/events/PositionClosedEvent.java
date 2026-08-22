package com.trading.portfolio.events;

import com.trading.signals.PositionStatus;

import java.math.BigDecimal;

public record PositionClosedEvent(Long positionId, String symbol,
                                  PositionStatus closeStatus, BigDecimal realisedPnl) {}
