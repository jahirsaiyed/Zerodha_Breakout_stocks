package com.trading.signals.dto;

import com.trading.signals.Signal;
import com.trading.signals.SignalSource;
import com.trading.signals.SignalStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SignalResponse(
        Long id,
        String symbol,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal target,
        BigDecimal riskRewardRatio,
        SignalSource source,
        String sourceRef,
        SignalStatus status,
        String notes,
        LocalDateTime addedAt,
        LocalDateTime updatedAt
) {
    public static SignalResponse from(Signal signal) {
        return new SignalResponse(
                signal.getId(),
                signal.getSymbol(),
                signal.getEntryPrice(),
                signal.getStopLoss(),
                signal.getTarget(),
                signal.getRiskRewardRatio(),
                signal.getSource(),
                signal.getSourceRef(),
                signal.getStatus(),
                signal.getNotes(),
                signal.getAddedAt(),
                signal.getUpdatedAt()
        );
    }
}
