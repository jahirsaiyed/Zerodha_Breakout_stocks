package com.trading.auth;

import com.trading.auth.dto.LoginRequest;
import com.trading.auth.dto.RefreshRequest;
import com.trading.auth.dto.TokenResponse;
import com.trading.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Mobile Auth", description = "Token-based auth for mobile clients")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    @PostMapping("/token")
    @Operation(summary = "Issue access + refresh token pair (mobile login)")
    public ResponseEntity<ApiResponse<TokenResponse>> token(@RequestBody @Valid LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success(mobileAuthService.login(req)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token — returns new token pair")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody @Valid RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse.success(mobileAuthService.refresh(req.refreshToken())));
    }

    @PostMapping("/revoke")
    @Operation(summary = "Revoke a refresh token (mobile logout)")
    public ResponseEntity<ApiResponse<Void>> revoke(@RequestBody @Valid RefreshRequest req) {
        mobileAuthService.revoke(req.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
