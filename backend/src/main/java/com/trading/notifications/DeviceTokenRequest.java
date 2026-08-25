package com.trading.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRequest(
    @NotBlank String token,
    @NotNull DeviceToken.Platform platform
) {}
