package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.users.dto.CreateUserRequest;
import com.trading.users.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
