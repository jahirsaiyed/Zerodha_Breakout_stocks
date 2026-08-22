package com.trading.auth;

import com.trading.auth.dto.LoginRequest;
import com.trading.common.ApiResponse;
import com.trading.users.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login and logout")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates credentials and sets an HttpOnly JWT cookie")
    public ResponseEntity<ApiResponse<UserResponse>> login(
            @RequestBody @Valid LoginRequest req, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(req, response)));
    }

    @DeleteMapping("/logout")
    @Operation(summary = "Logout", description = "Clears the JWT cookie")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
