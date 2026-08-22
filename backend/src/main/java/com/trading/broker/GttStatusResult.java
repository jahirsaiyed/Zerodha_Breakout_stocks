package com.trading.broker;

import java.math.BigDecimal;

/**
 * Result of a GTT trigger status check.
 * {@code triggered} = true means the GTT was fired and the exit order executed.
 * {@code filledPrice} is the actual fill price of the exit order.
 */
public record GttStatusResult(boolean triggered, BigDecimal filledPrice) {
    public static GttStatusResult active() { return new GttStatusResult(false, null); }
}
