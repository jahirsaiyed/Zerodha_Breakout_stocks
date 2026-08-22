package com.trading.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends messages to a Telegram chat via the Bot API.
 * Silently skips when {@code telegram.enabled=false} or chatId is blank.
 * Exceptions are caught and logged — a failed notification must never
 * crash the portfolio engine or its event handlers.
 */
@Slf4j
@Service
public class TelegramApiClient {

    private final TelegramProperties props;
    private final RestTemplate restTemplate;

    public TelegramApiClient(TelegramProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendMessage(String chatId, String text) {
        if (!props.isEnabled()) return;
        if (chatId == null || chatId.isBlank()) return;
        if (props.getBotToken() == null || props.getBotToken().isBlank()) {
            log.warn("Telegram bot token is not configured — cannot send message");
            return;
        }

        String url = props.getBaseUrl() + "/bot" + props.getBotToken() + "/sendMessage";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForObject(url, request, String.class);
            log.debug("Telegram message sent to chatId={}", chatId);
        } catch (Exception e) {
            log.warn("Telegram sendMessage failed for chatId={}: {}", chatId, e.getMessage());
        }
    }
}
