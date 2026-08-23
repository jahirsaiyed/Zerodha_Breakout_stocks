package com.trading.notifications;

import jakarta.validation.constraints.NotBlank;

public record ConnectBotRequest(@NotBlank String botToken) {}
