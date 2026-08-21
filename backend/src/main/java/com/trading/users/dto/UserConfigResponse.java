package com.trading.users.dto;

import java.math.BigDecimal;

public record UserConfigResponse(
    Integer maxPositions,
    String positionSizingMethod,
    BigDecimal positionSizingValue,
    Integer orderExpiryDays,
    String telegramChatId,
    Boolean zerodhaConnected,
    String zerodhaApiKey
) {}
