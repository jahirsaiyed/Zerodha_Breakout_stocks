package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.portfolio.PortfolioDbService;
import com.trading.portfolio.PortfolioEngine;
import com.trading.portfolio.dto.ConfirmFillRequest;
import com.trading.portfolio.dto.PositionResponse;
import com.trading.signals.InstrumentCacheService;
import com.trading.signals.Position;
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
    private final PortfolioEngine portfolioEngine;
    private final PortfolioDbService portfolioDbService;

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

    @PostMapping("/portfolio/run-loop")
    @Operation(summary = "Manually trigger the core portfolio loop (places new entry orders)")
    public ResponseEntity<ApiResponse<Void>> runLoop() {
        portfolioEngine.runCoreLoop();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/portfolio/check-fills")
    @Operation(summary = "Manually trigger order fill detection")
    public ResponseEntity<ApiResponse<Void>> checkFills() {
        portfolioEngine.checkOrderFills();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/portfolio/reconcile-gtt")
    @Operation(summary = "Manually trigger GTT exit reconciliation")
    public ResponseEntity<ApiResponse<Void>> reconcileGtt() {
        portfolioEngine.reconcileGttExits();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/portfolio/positions/{id}/confirm-fill")
    @Operation(summary = "Manually confirm a fill for a position whose order can no longer be verified with Zerodha "
            + "(e.g. order_id aged past the trading day). The caller must independently verify the actual fill "
            + "on Zerodha first — this never queries the broker to decide fill status.")
    public ResponseEntity<ApiResponse<PositionResponse>> confirmFill(
            @PathVariable Long id, @RequestBody @Valid ConfirmFillRequest req) {
        portfolioEngine.confirmManualFill(id, req.quantity(), req.avgPrice());
        Position pos = portfolioDbService.getPositionById(id)
                .orElseThrow(() -> new IllegalStateException("Position not found after confirm-fill: " + id));
        return ResponseEntity.ok(ApiResponse.success(PositionResponse.from(pos)));
    }
}
