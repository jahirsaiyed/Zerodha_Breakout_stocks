package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.signals.InstrumentCacheService;
import com.trading.signals.SignalSyncLog;
import com.trading.signals.SignalSyncLogRepository;
import com.trading.users.dto.CreateUserRequest;
import com.trading.users.dto.HealthResponse;
import com.trading.users.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.trading.config.OpenApiConfig.COOKIE_AUTH;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin-only user management (requires ADMIN role)")
@SecurityRequirement(name = COOKIE_AUTH)
public class AdminController {
    private final UserService userService;
    private final UserConfigRepository userConfigRepository;
    private final SignalSyncLogRepository syncLogRepository;
    private final InstrumentCacheService instrumentCacheService;

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @PostMapping("/users")
    @Operation(summary = "Create a user")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody @Valid CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(req)));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Enable or disable a user")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable Long id, @RequestParam boolean active) {
        userService.setUserActive(id, active);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/health")
    @Operation(summary = "System health snapshot")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        List<SignalSyncLog> recent = syncLogRepository
                .findAllByOrderBySyncedAtDesc(PageRequest.of(0, 1));

        SignalSyncLog last = recent.isEmpty() ? null : recent.get(0);

        List<HealthResponse.UserZerodhaStatus> zerodhaStatuses = userConfigRepository.findAll().stream()
                .map(cfg -> new HealthResponse.UserZerodhaStatus(
                        cfg.getUser().getId(),
                        cfg.getUser().getEmail(),
                        Boolean.TRUE.equals(cfg.getZerodhaConnected())))
                .toList();

        HealthResponse health = new HealthResponse(
                instrumentCacheService.getCacheSize(),
                instrumentCacheService.getCacheSize() > 0,
                last != null ? last.getSyncedAt() : null,
                last != null ? last.getSignalsAdded() : 0,
                last != null ? last.getSignalsModified() : 0,
                zerodhaStatuses
        );

        return ResponseEntity.ok(ApiResponse.success(health));
    }
}
