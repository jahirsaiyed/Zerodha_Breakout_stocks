package com.trading.portfolio.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Position enriched with live LTP and unrealised P&L.
 * {@code ltp} and {@code unrealisedPnl} are null when Zerodha is not connected
 * or the quote fetch fails.
 */
public record LivePositionResponse(
        Long id,
        String symbol,
        int quantity,
        BigDecimal avgEntryPrice,
        BigDecimal signalStopLoss,
        BigDecimal signalTarget,
        String status,
        BigDecimal ltp,
        BigDecimal unrealisedPnl,
        BigDecimal breakevenSl
) {
    public static LivePositionResponse of(
            com.trading.signals.Position pos, BigDecimal ltp) {
        BigDecimal unrealised = null;
        if (ltp != null && pos.getAvgEntryPrice() != null) {
            unrealised = ltp.subtract(pos.getAvgEntryPrice())
                    .multiply(BigDecimal.valueOf(pos.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return new LivePositionResponse(
                pos.getId(),
                pos.getSymbol(),
                pos.getQuantity(),
                pos.getAvgEntryPrice(),
                pos.getSignal() != null ? pos.getSignal().getStopLoss() : null,
                pos.getSignal() != null ? pos.getSignal().getTarget() : null,
                pos.getStatus().name(),
                ltp,
                unrealised,
                pos.getBreakevenSl()
        );
    }
}
