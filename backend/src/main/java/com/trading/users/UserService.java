package com.trading.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.EncryptionUtil;
import com.trading.notifications.TelegramProperties;
import com.trading.users.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserConfigRepository userConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;
    private final TelegramProperties telegramProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already registered: " + req.email());
        }
        User user = User.builder()
                .name(req.name()).email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(req.role() != null ? req.role() : UserRole.USER)
                .active(true).build();
        user = userRepository.save(user);
        userConfigRepository.save(UserConfig.builder().user(user).build());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        return toResponse(findByEmail(email));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void setUserActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setActive(active);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserConfigResponse getConfigByEmail(String email) {
        UserConfig cfg = userConfigRepository.findByUser_Email(email)
                .orElseThrow(() -> new IllegalArgumentException("Config not found for: " + email));
        return toConfigResponse(cfg);
    }

    @Transactional
    public UserConfigResponse updateConfig(String email, UpdateConfigRequest req) {
        UserConfig cfg = userConfigRepository.findByUser_Email(email)
                .orElseThrow(() -> new IllegalArgumentException("Config not found for: " + email));
        if (req.maxPositions() != null) cfg.setMaxPositions(req.maxPositions());
        if (req.positionSizingMethod() != null) cfg.setPositionSizingMethod(req.positionSizingMethod());
        if (req.positionSizingValue() != null) cfg.setPositionSizingValue(req.positionSizingValue());
        if (req.orderExpiryDays() != null) cfg.setOrderExpiryDays(req.orderExpiryDays());
        if (req.telegramChatId() != null) cfg.setTelegramChatId(req.telegramChatId());
        if (req.zerodhaTotpSecret() != null)
            cfg.setZerodhaTotpSecret(req.zerodhaTotpSecret().isBlank() ? null
                    : encryptionUtil.encrypt(req.zerodhaTotpSecret()));
        if (req.marginUsagePercent() != null) cfg.setMarginUsagePercent(req.marginUsagePercent());
        // marginUsageFixedLimit is always applied — null explicitly clears the cap
        cfg.setMarginUsageFixedLimit(req.marginUsageFixedLimit());
        if (req.tradingPaused() != null) cfg.setTradingPaused(req.tradingPaused());
        if (req.syncPaused()    != null) cfg.setSyncPaused(req.syncPaused());
        return toConfigResponse(userConfigRepository.save(cfg));
    }

    @Transactional
    public UserConfigResponse connectUserBot(String email, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Bot token must not be blank");
        }
        BotIdentity identity = fetchBotIdentity(rawToken.trim());
        UserConfig cfg = userConfigRepository.findByUser_Email(email)
                .orElseThrow(() -> new IllegalArgumentException("Config not found for: " + email));
        cfg.setTelegramBotToken(encryptionUtil.encrypt(rawToken.trim()));
        cfg.setTelegramBotUsername(identity.username());
        cfg.setTelegramBotName(identity.name());
        log.info("User {} connected Telegram bot: @{} ({})", email, identity.username(), identity.name());
        return toConfigResponse(userConfigRepository.save(cfg));
    }

    @Transactional
    public void disconnectUserBot(String email) {
        UserConfig cfg = userConfigRepository.findByUser_Email(email)
                .orElseThrow(() -> new IllegalArgumentException("Config not found for: " + email));
        log.info("User {} disconnected Telegram bot (was @{})", email, cfg.getTelegramBotUsername());
        cfg.setTelegramBotToken(null);
        cfg.setTelegramBotUsername(null);
        cfg.setTelegramBotName(null);
        userConfigRepository.save(cfg);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getActive());
    }

    private UserConfigResponse toConfigResponse(UserConfig cfg) {
        return new UserConfigResponse(
                cfg.getMaxPositions(), cfg.getPositionSizingMethod().name(),
                cfg.getPositionSizingValue(), cfg.getOrderExpiryDays(),
                cfg.getTelegramChatId(), cfg.getZerodhaConnected(),
                cfg.getZerodhaTotpSecret() != null && !cfg.getZerodhaTotpSecret().isBlank(),
                cfg.getTelegramBotToken() != null,
                cfg.getTelegramBotName(),
                cfg.getTelegramBotUsername(),
                cfg.getMarginUsagePercent(),
                cfg.getMarginUsageFixedLimit(),
                cfg.getTradingPaused(),
                cfg.getSyncPaused());
    }

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
            return new BotIdentity(result.path("username").asText(""), result.path("first_name").asText("Bot"));
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

    private record BotIdentity(String username, String name) {}
}
