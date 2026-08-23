package com.trading.users.dto;

import java.math.BigDecimal;

public record UserConfigResponse(
    Integer maxPositions,
    String positionSizingMethod,
    BigDecimal positionSizingValue,
    Integer orderExpiryDays,
    String telegramChatId,
    Boolean zerodhaConnected,
    Boolean hasTotpSecret,
    Boolean hasBotToken,
    String botName,
    String botUsername,
    BigDecimal marginUsagePercent,
    BigDecimal marginUsageFixedLimit
) {}
