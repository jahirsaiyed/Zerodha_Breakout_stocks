package com.trading.users.dto;

import com.trading.users.PositionSizingMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateConfigRequest(
    @Min(1) @Max(50) Integer maxPositions,
    PositionSizingMethod positionSizingMethod,
    @DecimalMin("1000") BigDecimal positionSizingValue,
    @Min(1) @Max(30) Integer orderExpiryDays,
    String telegramChatId,
    String zerodhaTotpSecret
) {}
