package com.trading.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Manages the Telegram bot configuration stored in {@code telegram_bot_config}.
 *
 * <p>The bot token is encrypted at rest with AES-GCM via {@link EncryptionUtil}.
 * When connecting a new bot, the token is validated against Telegram's {@code getMe}
 * API to confirm it is valid before being persisted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotConfigService {

    private final TelegramBotConfigRepository repository;
    private final TelegramProperties telegramProperties;
    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Read ─────────────────────────────────────────────────────────────────

    /** Returns the public config DTO (token is never included). */
    @Transactional(readOnly = true)
    public TelegramBotConfigDto getConfig() {
        return repository.findFirstBy()
                .map(this::toDto)
                .orElse(new TelegramBotConfigDto(null, null, false, false));
    }

    /**
     * Returns the decrypted bot token if the bot is enabled and a token is stored.
     * Called at runtime by {@link TelegramApiClient} and {@link TelegramBotService}.
     */
    @Transactional(readOnly = true)
    public Optional<String> getActiveToken() {
        return repository.findFirstBy()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .filter(c -> c.getBotToken() != null)
                .map(c -> encryptionUtil.decrypt(c.getBotToken()));
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Validates {@code rawToken} against Telegram's {@code getMe} endpoint,
     * then encrypts and persists it along with the bot's name and username.
     * Enables the bot automatically on success.
     *
     * @throws IllegalArgumentException if the token is rejected by Telegram
     */
    @Transactional
    public TelegramBotConfigDto connectBot(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Bot token must not be blank");
        }

        BotIdentity identity = fetchBotIdentity(rawToken.trim());

        TelegramBotConfig config = repository.findFirstBy()
                .orElseGet(() -> TelegramBotConfig.builder().build());

        config.setBotToken(encryptionUtil.encrypt(rawToken.trim()));
        config.setBotUsername(identity.username());
        config.setBotName(identity.name());
        config.setEnabled(true);

        repository.save(config);
        log.info("Telegram bot connected: @{} ({})", identity.username(), identity.name());
        return toDto(config);
    }

    /** Clears the stored token and disables the bot. */
    @Transactional
    public void disconnectBot() {
        repository.findFirstBy().ifPresent(config -> {
            log.info("Telegram bot disconnected (was @{})", config.getBotUsername());
            config.setBotToken(null);
            config.setBotUsername(null);
            config.setBotName(null);
            config.setEnabled(false);
            repository.save(config);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BotIdentity fetchBotIdentity(String rawToken) {
        String url = telegramProperties.getBaseUrl() + "/bot" + rawToken + "/getMe";
        RestTemplate restTemplate = buildRestTemplate();
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (!root.path("ok").asBoolean(false)) {
                throw new IllegalArgumentException(
                        "Telegram rejected the token: " + root.path("description").asText("unknown error"));
            }
            JsonNode result = root.path("result");
            String username = result.path("username").asText("");
            String firstName = result.path("first_name").asText("Bot");
            return new BotIdentity(username, firstName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not reach Telegram to validate the token — check the token and your network: "
                            + e.getMessage());
        }
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    private TelegramBotConfigDto toDto(TelegramBotConfig config) {
        return new TelegramBotConfigDto(
                config.getBotName(),
                config.getBotUsername(),
                Boolean.TRUE.equals(config.getEnabled()),
                config.getBotToken() != null
        );
    }

    private record BotIdentity(String username, String name) {}
}
