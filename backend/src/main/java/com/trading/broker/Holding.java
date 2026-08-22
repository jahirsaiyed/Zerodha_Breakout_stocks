package com.trading.broker;

import java.math.BigDecimal;

public record Holding(
        String symbol,
        int quantity,
        BigDecimal avgPrice,
        BigDecimal lastPrice
) {}
