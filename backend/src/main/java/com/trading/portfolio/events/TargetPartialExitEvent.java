package com.trading.portfolio.events;

import java.math.BigDecimal;

/**
 * Fired when the target GTT triggers and the user's configured booking percentage of the
 * position is sold. The remainder stays ACTIVE with the stop-loss moved to breakeven (avgEntryPrice).
 */
public record TargetPartialExitEvent(
        Long positionId,
        String symbol,
        int soldQty,
        int remainingQty,
        BigDecimal fillPrice,
        BigDecimal breakevenSl
) {}
