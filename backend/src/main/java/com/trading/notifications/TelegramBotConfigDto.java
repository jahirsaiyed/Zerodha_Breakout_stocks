package com.trading.notifications;

/**
 * Public view of the Telegram bot configuration.
 * The raw token is never exposed — {@code hasToken} indicates whether one is stored.
 *
 * @param botName     display name from Telegram getMe, null if not configured
 * @param botUsername username without @ prefix, null if not configured
 * @param enabled     whether the bot is active
 * @param hasToken    true if an encrypted token is stored in the database
 */
public record TelegramBotConfigDto(
        String botName,
        String botUsername,
        boolean enabled,
        boolean hasToken
) {}
