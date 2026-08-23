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

/**
 * Sends messages to a Telegram chat via the Bot API.
 *
 * <p>Two variants:
 * <ul>
 *   <li>{@link #sendMessage(String, String, String)} — explicit token, used for per-user bots</li>
 *   <li>{@link #sendMessage(String, String)} — uses the admin bot token from {@link TelegramBotConfigService}</li>
 * </ul>
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
     * Sends a text message using an explicit bot token (per-user bot).
     *
     * @return the effective chat ID delivered to, or {@code null} on failure
     */
    public String sendMessage(String token, String chatId, String text) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) return null;
        String url = telegramProperties.getBaseUrl() + "/bot" + token + "/sendMessage";
        return doSend(url, chatId, text);
    }

    /**
     * Sends a text message using the admin-level bot token from {@link TelegramBotConfigService}.
     * Silently skips when no admin token is configured or the bot is disabled.
     *
     * @return the effective chat ID delivered to, or {@code null} on failure
     */
    public String sendMessage(String chatId, String text) {
        if (chatId == null || chatId.isBlank()) return null;

        return botConfigService.getActiveToken()
                .map(token -> {
                    String url = telegramProperties.getBaseUrl() + "/bot" + token + "/sendMessage";
                    return doSend(url, chatId, text);
                })
                .orElseGet(() -> {
                    log.debug("Telegram admin bot not configured or disabled — message skipped");
                    return null;
                });
    }

    private String doSend(String url, String chatId, String text) {
        try {
            post(url, chatId, text);
            log.debug("Telegram message sent to chatId={}", chatId);
            return chatId;
        } catch (HttpClientErrorException.BadRequest e) {
            String migratedId = extractMigratedChatId(e.getResponseBodyAsString());
            if (migratedId != null) {
                log.info("Chat {} migrated to supergroup {} — retrying with new ID", chatId, migratedId);
                try {
                    post(url, migratedId, text);
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

    private void post(String url, String chatId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
    }

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
