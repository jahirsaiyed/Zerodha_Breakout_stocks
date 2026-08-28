package com.trading.portfolio.dto;

import java.math.BigDecimal;

public record OrderPreviewResponse(
        Long signalId,
        String symbol,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal target,
        BigDecimal riskRewardRatio,
        Integer estimatedQty,
        BigDecimal estimatedCost,
        Integer availableSlots,
        BigDecimal availableMargin,
        boolean canPlace,
        String reason,
        String warning
) {}
