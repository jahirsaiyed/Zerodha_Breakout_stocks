package com.trading.portfolio.events;

import java.math.BigDecimal;

/**
 * Fired when the target GTT triggers and 50% of the position is sold.
 * The remaining half stays ACTIVE with the stop-loss moved to breakeven (avgEntryPrice).
 */
public record TargetPartialExitEvent(
        Long positionId,
        String symbol,
        int soldQty,
        int remainingQty,
        BigDecimal fillPrice,
        BigDecimal breakevenSl
) {}
