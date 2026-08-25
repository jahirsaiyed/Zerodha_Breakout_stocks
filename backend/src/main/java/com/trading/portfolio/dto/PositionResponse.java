package com.trading.portfolio.dto;

import com.trading.signals.Position;
import com.trading.signals.PositionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionResponse(
        Long id,
        String symbol,
        Integer quantity,
        BigDecimal avgEntryPrice,
        String entryOrderId,
        String gttOrderId,
        PositionStatus status,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        BigDecimal realisedPnl,
        Long signalId,
        BigDecimal signalEntryPrice,
        BigDecimal signalStopLoss,
        BigDecimal signalTarget,
        BigDecimal breakevenSl
) {
    public static PositionResponse from(Position pos) {
        var signal = pos.getSignal();
        return new PositionResponse(
                pos.getId(),
                pos.getSymbol(),
                pos.getQuantity(),
                pos.getAvgEntryPrice(),
                pos.getEntryOrderId(),
                pos.getGttOrderId(),
                pos.getStatus(),
                pos.getOpenedAt(),
                pos.getClosedAt(),
                pos.getRealisedPnl(),
                signal != null ? signal.getId() : null,
                signal != null ? signal.getEntryPrice() : null,
                signal != null ? signal.getStopLoss() : null,
                signal != null ? signal.getTarget() : null,
                pos.getBreakevenSl()
        );
    }
}
