package com.trading.users;

import com.trading.broker.BrokerAdapterFactory;
import com.trading.broker.BrokerTokenException;
import com.trading.common.ApiResponse;
import com.trading.notifications.NotificationService;
import com.trading.portfolio.PortfolioDbService;
import com.trading.users.dto.AccountSummaryResponse;
import com.trading.users.dto.ChangePasswordRequest;
import com.trading.users.dto.UpdateConfigRequest;
import com.trading.users.dto.UserConfigResponse;
import com.trading.users.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

import static com.trading.config.OpenApiConfig.COOKIE_AUTH;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current user profile and configuration")
@SecurityRequirement(name = COOKIE_AUTH)
public class UserController {
    private final UserService userService;
    private final NotificationService notificationService;
    private final BrokerAdapterFactory brokerAdapterFactory;
    private final PortfolioDbService portfolioDbService;

    @GetMapping("/me")
    @Operation(summary = "Get current user")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByEmail(auth.getName())));
    }

    @GetMapping("/me/config")
    @Operation(summary = "Get current user config")
    public ResponseEntity<ApiResponse<UserConfigResponse>> getMyConfig(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userService.getConfigByEmail(auth.getName())));
    }

    @PutMapping("/me/config")
    @Operation(summary = "Update current user config")
    public ResponseEntity<ApiResponse<UserConfigResponse>> updateMyConfig(
            Authentication auth, @RequestBody @Valid UpdateConfigRequest req) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateConfig(auth.getName(), req)));
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change current user's password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication auth, @RequestBody @Valid ChangePasswordRequest req) {
        userService.changePassword(auth.getName(), req.currentPassword(), req.newPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/me/telegram/test")
    @Operation(summary = "Send a test Telegram message to the current user")
    public ResponseEntity<ApiResponse<Void>> testTelegram(Authentication auth) {
        Long userId = userService.getUserByEmail(auth.getName()).id();
        notificationService.notifyUser(userId,
                "Test message from Zerodha Breakout — your Telegram notifications are working.");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me/account-summary")
    @Operation(summary = "Get account summary: available margin and position slot usage")
    public ResponseEntity<ApiResponse<AccountSummaryResponse>> getAccountSummary(Authentication auth) {
        String email = auth.getName();
        Long userId = portfolioDbService.getUserIdByEmail(email);
        long activePositions = portfolioDbService.countActivePositions(userId);

        Optional<UserConfig> configOpt = portfolioDbService.getUserConfigByUserId(userId);
        Integer maxPositions = configOpt.map(UserConfig::getMaxPositions).orElse(null);
        BigDecimal positionSizingValue = configOpt.map(UserConfig::getPositionSizingValue).orElse(null);

        BigDecimal availableMargin = null;
        if (configOpt.isPresent() && Boolean.TRUE.equals(configOpt.get().getZerodhaConnected())) {
            try {
                availableMargin = brokerAdapterFactory.forUser(configOpt.get()).getAvailableMargin();
            } catch (BrokerTokenException e) {
                log.debug("Margin unavailable for {} — token issue: {}", email, e.getMessage());
            } catch (Exception e) {
                log.debug("Margin unavailable for {} ({}): {}", email, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                new AccountSummaryResponse(availableMargin, activePositions, maxPositions, positionSizingValue)));
    }
}
