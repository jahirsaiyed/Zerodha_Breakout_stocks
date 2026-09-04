package com.trading.portfolio.dto;

import com.trading.signals.dto.CreateSignalRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Records a manually-executed trade with no pre-existing tracked signal: the signal is created
 * (source=MANUAL) and the fill recorded against it in one call.
 */
public record CreateManualOrderRequest(
        @NotNull @Valid CreateSignalRequest signal,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive BigDecimal avgPrice
) {}
