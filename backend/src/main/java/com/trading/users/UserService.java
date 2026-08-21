package com.trading.users;

import com.trading.common.EncryptionUtil;
import com.trading.users.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserConfigRepository userConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already registered: " + req.email());
        }
        User user = User.builder()
                .name(req.name()).email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(req.role() != null ? req.role() : User.UserRole.USER)
                .active(true).build();
        user = userRepository.save(user);
        userConfigRepository.save(UserConfig.builder().user(user).build());
        return toResponse(user);
    }

    public UserResponse getUserByEmail(String email) {
        return toResponse(findByEmail(email));
    }

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
        if (req.zerodhaApiKey() != null) cfg.setZerodhaApiKey(req.zerodhaApiKey());
        if (req.zerodhaApiSecret() != null)
            cfg.setZerodhaApiSecret(encryptionUtil.encrypt(req.zerodhaApiSecret()));
        cfg.setUpdatedAt(LocalDateTime.now());
        return toConfigResponse(userConfigRepository.save(cfg));
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
                cfg.getTelegramChatId(), cfg.getZerodhaConnected(), cfg.getZerodhaApiKey());
    }
}
