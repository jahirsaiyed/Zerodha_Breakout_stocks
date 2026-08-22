package com.trading.broker;

import java.math.BigDecimal;

/**
 * Full detail of a broker order — status, fill quantity, and average fill price.
 * Used by fill-detection logic to handle full and partial fills.
 */
public record BrokerOrderDetail(
        BrokerOrderStatus status,
        int filledQuantity,
        BigDecimal avgPrice
) {
    public boolean isFullyFilled() { return status == BrokerOrderStatus.COMPLETE; }
    public boolean isPartiallyFilled() {
        return status == BrokerOrderStatus.PENDING && filledQuantity > 0;
    }
    public boolean isFailed() {
        return status == BrokerOrderStatus.CANCELLED || status == BrokerOrderStatus.REJECTED;
    }
}
