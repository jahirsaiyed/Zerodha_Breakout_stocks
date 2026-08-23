package com.trading.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Static Telegram infrastructure properties from {@code application.yml}.
 *
 * <p>The bot token and enabled flag are now managed in the database via
 * {@link TelegramBotConfig} and configured through the admin UI.
 * Only the API base URL remains here as it is infrastructure-level and
 * rarely needs to change.
 */
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    private String baseUrl = "https://api.telegram.org";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
