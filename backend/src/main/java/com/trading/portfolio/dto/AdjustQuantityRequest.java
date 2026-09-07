package com.trading.portfolio.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdjustQuantityRequest(
        @NotNull @Positive Integer quantity
) {}
