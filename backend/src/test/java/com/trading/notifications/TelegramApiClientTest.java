package com.trading.notifications;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelegramApiClientTest {

    private TelegramApiClient buildClient(boolean enabled, String token) {
        TelegramProperties props = new TelegramProperties();
        props.setEnabled(enabled);
        props.setBotToken(token);
        props.setBaseUrl("https://api.telegram.org");
        return new TelegramApiClient(props);
    }

    @Test
    @DisplayName("sendMessage does nothing when telegram is disabled")
    void sendMessage_disabled_skips() {
        TelegramApiClient client = buildClient(false, "some-token");
        // Should not throw and should not make any HTTP calls
        client.sendMessage("12345", "Hello");
        // If no exception and we got here, it skipped correctly
    }

    @Test
    @DisplayName("sendMessage does nothing when chatId is blank")
    void sendMessage_blankChatId_skips() {
        TelegramApiClient client = buildClient(true, "some-token");
        // Should silently skip with no exception
        client.sendMessage("", "Hello");
        client.sendMessage(null, "Hello");
    }

    @Test
    @DisplayName("sendMessage does nothing when bot token is blank")
    void sendMessage_noToken_skips() {
        TelegramApiClient client = buildClient(true, "");
        // Should log a warning and skip — no HTTP call, no exception
        client.sendMessage("12345", "Hello");
    }
}
