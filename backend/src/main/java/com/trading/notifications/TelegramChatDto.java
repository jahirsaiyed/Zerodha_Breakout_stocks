package com.trading.notifications;

/**
 * A Telegram chat discovered via bot updates.
 *
 * @param chatId    Telegram chat ID (numeric string)
 * @param chatTitle Display name (group/channel title, or user first name for private chats)
 * @param chatType  Telegram chat type: "private", "group", "supergroup", or "channel"
 */
public record TelegramChatDto(String chatId, String chatTitle, String chatType) {}
