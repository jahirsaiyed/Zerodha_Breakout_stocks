package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.users.dto.UpdateConfigRequest;
import com.trading.users.dto.UserConfigResponse;
import com.trading.users.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static com.trading.config.OpenApiConfig.COOKIE_AUTH;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current user profile and configuration")
@SecurityRequirement(name = COOKIE_AUTH)
public class UserController {
    private final UserService userService;

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
}
