package com.trading.signals.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateSignalRequest(
        @Positive(message = "entryPrice must be positive")
        BigDecimal entryPrice,

        @Positive(message = "stopLoss must be positive")
        BigDecimal stopLoss,

        @Positive(message = "target must be positive")
        BigDecimal target,

        String notes
) {}
