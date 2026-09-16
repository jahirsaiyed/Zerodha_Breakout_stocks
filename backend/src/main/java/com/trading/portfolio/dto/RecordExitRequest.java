package com.trading.portfolio.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecordExitRequest(
        @NotNull @Positive BigDecimal exitPrice
) {}
