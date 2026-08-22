package com.trading.signals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSignalRequest(
        @NotBlank(message = "symbol is required")
        String symbol,

        @NotNull(message = "entryPrice is required")
        @Positive(message = "entryPrice must be positive")
        BigDecimal entryPrice,

        @NotNull(message = "stopLoss is required")
        @Positive(message = "stopLoss must be positive")
        BigDecimal stopLoss,

        @NotNull(message = "target is required")
        @Positive(message = "target must be positive")
        BigDecimal target,

        String notes
) {}
