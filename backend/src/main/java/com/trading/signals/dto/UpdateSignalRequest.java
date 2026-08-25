package com.trading.signals.dto;

import com.trading.signals.StopLossBasis;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateSignalRequest(
        @Positive(message = "entryPrice must be positive")
        BigDecimal entryPrice,

        @Positive(message = "stopLoss must be positive")
        BigDecimal stopLoss,

        @Positive(message = "target must be positive")
        BigDecimal target,

        StopLossBasis closingBasis,

        String notes
) {}
