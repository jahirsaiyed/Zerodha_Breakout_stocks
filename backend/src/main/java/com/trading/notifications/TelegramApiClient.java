package com.trading.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sends messages to a Telegram chat via the Bot API.
 *
 * <p>The bot token is read at runtime from {@link TelegramBotConfigService}, so it can
 * be changed through the admin UI without restarting the application.
 * Silently skips when no token is configured or the bot is disabled.
 *
 * <p>Exceptions are caught and logged — a failed notification must never
 * crash the portfolio engine or its event handlers.
 *
 * <p>Returns the effective chat ID used (which may differ from the input when
 * Telegram signals a group→supergroup migration via {@code migrate_to_chat_id}).
 * Returns {@code null} on any unrecoverable failure.
 */
@Slf4j
@Service
public class TelegramApiClient {

    private final TelegramBotConfigService botConfigService;
    private final TelegramProperties telegramProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramApiClient(TelegramBotConfigService botConfigService,
                             TelegramProperties telegramProperties) {
        this.botConfigService = botConfigService;
        this.telegramProperties = telegramProperties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Sends a text message to the given Telegram chat.
     *
     * @return the effective chat ID the message was delivered to (may be a new supergroup ID
     *         if the original group was migrated), or {@code null} if delivery failed
     */
    public String sendMessage(String chatId, String text) {
        if (chatId == null || chatId.isBlank()) return null;

        Optional<String> tokenOpt = botConfigService.getActiveToken();
        if (tokenOpt.isEmpty()) {
            log.debug("Telegram bot not configured or disabled — message skipped");
            return null;
        }
        String token = tokenOpt.get();
        String url = telegramProperties.getBaseUrl() + "/bot" + token + "/sendMessage";

        try {
            doPost(url, chatId, text);
            log.debug("Telegram message sent to chatId={}", chatId);
            return chatId;
        } catch (HttpClientErrorException.BadRequest e) {
            String migratedId = extractMigratedChatId(e.getResponseBodyAsString());
            if (migratedId != null) {
                log.info("Chat {} migrated to supergroup {} — retrying with new ID", chatId, migratedId);
                try {
                    doPost(url, migratedId, text);
                    log.debug("Telegram message sent to migrated chatId={}", migratedId);
                    return migratedId;
                } catch (Exception retryEx) {
                    log.warn("Telegram sendMessage failed after migration for chatId={}: {}",
                            migratedId, retryEx.getMessage());
                    return null;
                }
            }
            log.warn("Telegram sendMessage failed for chatId={}: {}", chatId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Telegram sendMessage failed for chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    private void doPost(String url, String chatId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForObject(url, request, String.class);
    }

    /**
     * Parses a Telegram 400 error body and returns the {@code migrate_to_chat_id}
     * value if present, or {@code null} otherwise.
     */
    private String extractMigratedChatId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            long migratedId = root.path("parameters").path("migrate_to_chat_id").asLong(0);
            return migratedId != 0 ? String.valueOf(migratedId) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
