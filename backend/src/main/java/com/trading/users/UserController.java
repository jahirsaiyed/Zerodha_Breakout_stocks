package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.users.dto.UpdateConfigRequest;
import com.trading.users.dto.UserConfigResponse;
import com.trading.users.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByEmail(auth.getName())));
    }

    @GetMapping("/me/config")
    public ResponseEntity<ApiResponse<UserConfigResponse>> getMyConfig(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userService.getConfigByEmail(auth.getName())));
    }

    @PutMapping("/me/config")
    public ResponseEntity<ApiResponse<UserConfigResponse>> updateMyConfig(
            Authentication auth, @RequestBody @Valid UpdateConfigRequest req) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateConfig(auth.getName(), req)));
    }
}
