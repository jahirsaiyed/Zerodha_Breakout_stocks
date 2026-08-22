package com.trading.portfolio.events;

import java.math.BigDecimal;

public record UnmanagedPositionEvent(Long userId, String symbol,
                                     int quantity, BigDecimal lastPrice) {}
