package com.trading.notifications;

import com.trading.common.ApiResponse;
import com.trading.portfolio.PortfolioDbService;
import com.trading.users.User;
import com.trading.users.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Device Tokens", description = "Register/deregister push notification device tokens")
public class DeviceTokenController {

    private final DeviceTokenRepository deviceTokenRepository;
    private final PortfolioDbService portfolioDbService;
    private final UserRepository userRepository;

    @PostMapping("/push-token")
    @Operation(summary = "Register a FCM or APNs device token")
    public ResponseEntity<ApiResponse<Void>> register(
            Authentication auth, @RequestBody @Valid DeviceTokenRequest req) {
        Long userId = portfolioDbService.getUserIdByEmail(auth.getName());
        User user = userRepository.getReferenceById(userId);
        try {
            deviceTokenRepository.save(DeviceToken.builder()
                    .user(user).token(req.token()).platform(req.platform()).build());
        } catch (DataIntegrityViolationException ignored) {
            // Token already registered — treat as idempotent success
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/push-token")
    @Operation(summary = "Deregister a device token")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deregister(
            Authentication auth, @RequestBody @Valid DeviceTokenRequest req) {
        Long userId = portfolioDbService.getUserIdByEmail(auth.getName());
        deviceTokenRepository.deleteByUser_IdAndToken(userId, req.token());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
