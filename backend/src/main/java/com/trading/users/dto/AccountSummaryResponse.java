package com.trading.users.dto;

import java.math.BigDecimal;

public record AccountSummaryResponse(
    BigDecimal availableMargin,
    long activePositions,
    Integer maxPositions,
    BigDecimal positionSizingValue
) {}
