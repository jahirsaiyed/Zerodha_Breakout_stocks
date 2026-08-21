# Task 6 Brief: User Config + Admin API

## Context
Task 6 of 7 in Phase 1 (Foundation) of a Trading Portfolio Management System.
Project root: `D:\Zerodha_Breakout_stocks`
Branch: main

## Global Constraints
- Java 21, Spring Boot 3.3.5, package root `com.trading`
- All REST endpoints prefixed `/api`
- All API responses: `{ "success": true|false, "data": ..., "error": null|string }` — use `ApiResponse<T>` from `com.trading.common.ApiResponse`
- JWT in `httpOnly` cookie named `jwt`; 24-hour expiry; `SameSite=Strict`
- Schema owned exclusively by Flyway — `ddl-auto: validate` always

## What already exists (do NOT recreate)
- `com.trading.common.ApiResponse<T>` — record with `success(T data)` and `error(String msg)` factory methods
- `com.trading.users.UserService` — methods: `getUserByEmail(String)`, `getAllUsers()`, `setUserActive(Long, boolean)`, `getConfigByEmail(String)`, `updateConfig(String, UpdateConfigRequest)`, `createUser(CreateUserRequest)`
- `com.trading.users.dto.UserResponse` — record(Long id, String name, String email, String role, Boolean active)
- `com.trading.users.dto.UserConfigResponse` — record(Integer maxPositions, String positionSizingMethod, BigDecimal positionSizingValue, Integer orderExpiryDays, String telegramChatId, Boolean zerodhaConnected, String zerodhaApiKey)
- `com.trading.users.dto.CreateUserRequest` — record(@NotBlank name, @Email @NotBlank email, @NotBlank @Size(min=8) password, UserRole role)
- `com.trading.users.dto.UpdateConfigRequest` — record with optional nullable fields
- `com.trading.config.SecurityConfig` — `/api/admin/**` requires ADMIN role; everything else requires authenticated
- Spring Security: `Authentication.getName()` returns the user's email (the JWT subject)

## Files to Create

### Production code
- `backend/src/main/java/com/trading/users/UserController.java`
- `backend/src/main/java/com/trading/users/AdminController.java`

### Tests
There are no new test files in this task — the smoke tests are manual curl commands. The existing 9 tests (AuthControllerTest 4, EncryptionUtilTest 2, UserServiceTest 3) must still pass after these changes.

## Exact Implementation

### UserController.java
```java
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
```

### AdminController.java
```java
package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.users.dto.CreateUserRequest;
import com.trading.users.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody @Valid CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(req)));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable Long id, @RequestParam boolean active) {
        userService.setUserActive(id, active);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

## Verification
After creating files, run all tests to confirm nothing is broken:
```bash
cd /d/Zerodha_Breakout_stocks/backend && ./mvnw test
```
Expected: BUILD SUCCESS, all 9 existing tests still pass (no new automated tests in this task).

## Commit message
```
feat: user config API and admin user management endpoints
```

## Report
Write your report to: `D:\Zerodha_Breakout_stocks\.superpowers\sdd\task-6-report.md`

Include:
- STATUS: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Files created (list)
- Test results (exact Maven output lines showing total tests run)
- Any deviations from the plan
- Commits made (hash + message)
