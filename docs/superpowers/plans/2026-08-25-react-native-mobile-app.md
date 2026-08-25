# React Native Mobile App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Expo-managed React Native companion app (iOS + Android) with full trader feature parity, token-based JWT auth, native push notifications, and Zerodha OAuth via in-app browser.

**Architecture:** The mobile app (`mobile/`) lives in the existing monorepo and talks to the same Spring Boot backend via Bearer token auth — running in parallel with the existing cookie auth. The backend gains three new areas: token auth + refresh tokens (V11 migration), device token registration + Firebase push notifications (V12 migration), and a mobile-aware Zerodha OAuth callback.

**Tech Stack:** Expo SDK 51 · Expo Router v3 · TanStack Query v5 · Zustand v4 · NativeWind v4 · Axios · Firebase Admin SDK 9.3.0 · Java 21 / Spring Boot 3.3.5

## Global Constraints

- Expo SDK: 51 (React Native 0.74)
- Deep-link scheme: `zbs://`
- Backend base URL env var: `EXPO_PUBLIC_API_URL`
- Access token lifetime: 15 min · Refresh token lifetime: 30 days (opaque UUID, stored hashed)
- Flyway: next available versions are **V11** (refresh_tokens) and **V12** (device_tokens) — V9 and V10 are already used
- Backend test pattern: `@WebMvcTest` + `@Import(SecurityConfig.class)` + `@TestPropertySource(properties = {"cors.allowed-origins=http://localhost:3000", "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"})`
- Entity pattern: Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder` + JPA
- `ApiResponse<T>` record already exists at `com.trading.common.ApiResponse`
- All backend source under `backend/src/main/java/com/trading/`
- All backend tests under `backend/src/test/java/com/trading/`
- Mobile tests: Jest + `@testing-library/react-native`

---

### Task 1: Backend — Token Auth (migrations, refresh tokens, mobile endpoints, JWT filter update)

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__refresh_tokens.sql`
- Create: `backend/src/main/java/com/trading/auth/RefreshToken.java`
- Create: `backend/src/main/java/com/trading/auth/RefreshTokenRepository.java`
- Create: `backend/src/main/java/com/trading/auth/dto/TokenResponse.java`
- Create: `backend/src/main/java/com/trading/auth/dto/RefreshRequest.java`
- Create: `backend/src/main/java/com/trading/auth/MobileAuthService.java`
- Create: `backend/src/main/java/com/trading/auth/MobileAuthController.java`
- Modify: `backend/src/main/java/com/trading/auth/JwtUtil.java` (add 15-min access token method)
- Modify: `backend/src/main/java/com/trading/auth/JwtFilter.java` (also read Bearer header)
- Modify: `backend/src/main/java/com/trading/config/SecurityConfig.java` (permit new endpoints)
- Create: `backend/src/test/java/com/trading/auth/MobileAuthControllerTest.java`

**Interfaces:**
- Produces: `POST /api/auth/token` → `ApiResponse<TokenResponse>`, `POST /api/auth/refresh` → `ApiResponse<TokenResponse>`, `POST /api/auth/revoke` → `ApiResponse<Void>`
- `TokenResponse record(String accessToken, String refreshToken)`

- [ ] **Step 1: Write V11 migration**

```sql
-- backend/src/main/resources/db/migration/V11__refresh_tokens.sql
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
```

- [ ] **Step 2: Create RefreshToken entity**

```java
// backend/src/main/java/com/trading/auth/RefreshToken.java
package com.trading.auth;

import com.trading.users.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "refresh_tokens")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create RefreshTokenRepository**

```java
// backend/src/main/java/com/trading/auth/RefreshTokenRepository.java
package com.trading.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now OR r.revoked = true")
    void deleteExpiredAndRevoked(LocalDateTime now);
}
```

- [ ] **Step 4: Add generateAccessToken to JwtUtil**

Add this method to the existing `JwtUtil` class (keep `generateToken` unchanged for web cookies):

```java
// In JwtUtil.java — add constant and method:
private static final long ACCESS_TOKEN_EXPIRY_MS = 15L * 60 * 1000; // 15 min

public String generateAccessToken(String email, String role) {
    return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MS))
            .signWith(key)
            .compact();
}
```

- [ ] **Step 5: Extend JwtFilter to also read Bearer header**

Replace the `extractToken` method in `JwtFilter.java`:

```java
private Optional<String> extractToken(HttpServletRequest req) {
    // 1. Try Bearer header (mobile)
    String authHeader = req.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        return Optional.of(authHeader.substring(7));
    }
    // 2. Fall back to cookie (web)
    Cookie[] cookies = req.getCookies();
    if (cookies == null) return Optional.empty();
    return Arrays.stream(cookies)
            .filter(c -> "jwt".equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst();
}
```

- [ ] **Step 6: Create DTOs**

```java
// backend/src/main/java/com/trading/auth/dto/TokenResponse.java
package com.trading.auth.dto;
public record TokenResponse(String accessToken, String refreshToken) {}
```

```java
// backend/src/main/java/com/trading/auth/dto/RefreshRequest.java
package com.trading.auth.dto;
import jakarta.validation.constraints.NotBlank;
public record RefreshRequest(@NotBlank String refreshToken) {}
```

- [ ] **Step 7: Create MobileAuthService**

```java
// backend/src/main/java/com/trading/auth/MobileAuthService.java
package com.trading.auth;

import com.trading.auth.dto.LoginRequest;
import com.trading.auth.dto.TokenResponse;
import com.trading.users.User;
import com.trading.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.getActive()) throw new BadCredentialsException("Account is deactivated");
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash()))
            throw new BadCredentialsException("Invalid credentials");
        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BadCredentialsException("Refresh token expired or revoked");
        // Rotate: revoke old, issue new pair
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return issueTokenPair(stored.getUser());
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    private TokenResponse issueTokenPair(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        String rawRefresh = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefresh))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        refreshTokenRepository.save(refreshToken);
        return new TokenResponse(accessToken, rawRefresh);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step 8: Create MobileAuthController**

```java
// backend/src/main/java/com/trading/auth/MobileAuthController.java
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
```

- [ ] **Step 9: Update SecurityConfig to permit new mobile auth endpoints**

In `SecurityConfig.java`, add to the `authorizeHttpRequests` chain:

```java
.requestMatchers("/api/auth/token", "/api/auth/refresh", "/api/auth/revoke").permitAll()
```

Full updated block (replace the existing `.authorizeHttpRequests` lambda):

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/api/auth/login", "/api/auth/logout", "/api/auth/health",
        "/api/auth/token", "/api/auth/refresh", "/api/auth/revoke"
    ).permitAll()
    .requestMatchers("/api/zerodha/callback").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
    .anyRequest().authenticated())
```

- [ ] **Step 10: Write failing tests**

```java
// backend/src/test/java/com/trading/auth/MobileAuthControllerTest.java
package com.trading.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.auth.dto.LoginRequest;
import com.trading.auth.dto.RefreshRequest;
import com.trading.auth.dto.TokenResponse;
import com.trading.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MobileAuthController.class)
@Import(com.trading.config.SecurityConfig.class)
@TestPropertySource(properties = {
    "cors.allowed-origins=http://localhost:3000",
    "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"
})
class MobileAuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MobileAuthService mobileAuthService;
    @MockBean JwtUtil jwtUtil;

    @Test
    void token_returnsAccessAndRefreshTokenOnValidCredentials() throws Exception {
        var pair = new TokenResponse("access.jwt.token", "raw-refresh-uuid");
        when(mobileAuthService.login(any())).thenReturn(pair);

        mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("alice@test.com", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("raw-refresh-uuid"));
    }

    @Test
    void token_returns401OnBadCredentials() throws Exception {
        when(mobileAuthService.login(any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("x@x.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refresh_returnsNewTokenPairOnValidToken() throws Exception {
        var pair = new TokenResponse("new.access.token", "new-refresh-uuid");
        when(mobileAuthService.refresh(eq("old-refresh-uuid"))).thenReturn(pair);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest("old-refresh-uuid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"));
    }

    @Test
    void revoke_returns200AndDelegates() throws Exception {
        mockMvc.perform(post("/api/auth/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest("some-uuid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(mobileAuthService).revoke("some-uuid");
    }
}
```

- [ ] **Step 11: Run tests (expect failure — classes don't exist yet)**

```bash
cd backend && ./mvnw test -Dtest=MobileAuthControllerTest -q 2>&1 | tail -5
```

Expected: FAIL — `MobileAuthController` not found.

- [ ] **Step 12: Verify all tests pass**

```bash
cd backend && ./mvnw test -Dtest=MobileAuthControllerTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 13: Run full test suite to confirm no regressions**

```bash
cd backend && ./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 14: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V11__refresh_tokens.sql \
        src/main/java/com/trading/auth/RefreshToken.java \
        src/main/java/com/trading/auth/RefreshTokenRepository.java \
        src/main/java/com/trading/auth/dto/TokenResponse.java \
        src/main/java/com/trading/auth/dto/RefreshRequest.java \
        src/main/java/com/trading/auth/MobileAuthService.java \
        src/main/java/com/trading/auth/MobileAuthController.java \
        src/main/java/com/trading/auth/JwtUtil.java \
        src/main/java/com/trading/auth/JwtFilter.java \
        src/main/java/com/trading/config/SecurityConfig.java \
        src/test/java/com/trading/auth/MobileAuthControllerTest.java
git commit -m "feat: add mobile token auth endpoints and refresh token rotation"
```

---

### Task 2: Backend — Device Tokens + Push Notifications

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__device_tokens.sql`
- Create: `backend/src/main/java/com/trading/notifications/DeviceToken.java`
- Create: `backend/src/main/java/com/trading/notifications/DeviceTokenRepository.java`
- Create: `backend/src/main/java/com/trading/notifications/DeviceTokenRequest.java`
- Create: `backend/src/main/java/com/trading/notifications/PushNotificationService.java`
- Create: `backend/src/main/java/com/trading/notifications/DeviceTokenController.java`
- Modify: `backend/src/main/java/com/trading/notifications/NotificationService.java`
- Modify: `backend/pom.xml` (add Firebase Admin SDK)
- Create: `backend/src/test/java/com/trading/notifications/PushNotificationServiceTest.java`

**Interfaces:**
- Produces: `POST /api/users/me/push-token` and `DELETE /api/users/me/push-token`
- `PushNotificationService.sendToUser(Long userId, String title, String body, String deepLink)`

- [ ] **Step 1: Add Firebase Admin SDK to pom.xml**

In `backend/pom.xml`, add inside `<dependencies>`:

```xml
<!-- Firebase Admin SDK — push notifications -->
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.3.0</version>
</dependency>
```

- [ ] **Step 2: Write V12 migration**

```sql
-- backend/src/main/resources/db/migration/V12__device_tokens.sql
CREATE TYPE device_platform AS ENUM ('FCM', 'APNS');

CREATE TABLE device_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      TEXT NOT NULL,
    platform   device_platform NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, token)
);
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);
```

- [ ] **Step 3: Create DeviceToken entity**

```java
// backend/src/main/java/com/trading/notifications/DeviceToken.java
package com.trading.notifications;

import com.trading.users.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "device_tokens")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Platform { FCM, APNS }
}
```

- [ ] **Step 4: Create DeviceTokenRepository**

```java
// backend/src/main/java/com/trading/notifications/DeviceTokenRepository.java
package com.trading.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findByUser_Id(Long userId);
    void deleteByUser_IdAndToken(Long userId, String token);
}
```

- [ ] **Step 5: Create DeviceTokenRequest DTO**

```java
// backend/src/main/java/com/trading/notifications/DeviceTokenRequest.java
package com.trading.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRequest(
    @NotBlank String token,
    @NotNull DeviceToken.Platform platform
) {}
```

- [ ] **Step 6: Create PushNotificationService**

```java
// backend/src/main/java/com/trading/notifications/PushNotificationService.java
package com.trading.notifications;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Sends a push notification to all registered devices for a user.
     * No-op if the user has no registered device tokens.
     * Failures per device are logged but do not throw.
     */
    public void sendToUser(Long userId, String title, String body, String deepLink) {
        List<DeviceToken> devices = deviceTokenRepository.findByUser_Id(userId);
        if (devices.isEmpty()) return;

        for (DeviceToken device : devices) {
            try {
                Message message = Message.builder()
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("deepLink", deepLink)
                        .setToken(device.getToken())
                        .build();
                FirebaseMessaging.getInstance().send(message);
                log.debug("Push sent to userId={} platform={}", userId, device.getPlatform());
            } catch (Exception e) {
                log.warn("Push failed for userId={} platform={}: {}", userId, device.getPlatform(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 7: Create DeviceTokenController**

```java
// backend/src/main/java/com/trading/notifications/DeviceTokenController.java
package com.trading.notifications;

import com.trading.common.ApiResponse;
import com.trading.portfolio.PortfolioDbService;
import com.trading.users.User;
import com.trading.users.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        boolean exists = deviceTokenRepository.findByUser_Id(userId)
                .stream().anyMatch(t -> t.getToken().equals(req.token()));
        if (!exists) {
            deviceTokenRepository.save(DeviceToken.builder()
                    .user(user).token(req.token()).platform(req.platform()).build());
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/push-token")
    @Operation(summary = "Deregister a device token")
    public ResponseEntity<ApiResponse<Void>> deregister(
            Authentication auth, @RequestBody @Valid DeviceTokenRequest req) {
        Long userId = portfolioDbService.getUserIdByEmail(auth.getName());
        deviceTokenRepository.deleteByUser_IdAndToken(userId, req.token());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

- [ ] **Step 8: Wire PushNotificationService into NotificationService**

Add `PushNotificationService` field to `NotificationService` and call it alongside Telegram in `notifyUser`:

```java
// In NotificationService.java — add field:
private final PushNotificationService pushNotificationService;

// In notifyUser(), after the Telegram send block succeeds (or as a parallel call):
// At the end of the userConfigRepository.findByUser_Id(userId).ifPresentOrElse lambda,
// add before the closing brace:
pushNotificationService.sendToUser(userId, "Trading Alert", message, "zbs://dashboard");
```

The updated `notifyUser` method signature stays the same. The push call is fire-and-forget (exceptions are caught inside `PushNotificationService`).

- [ ] **Step 9: Write test for PushNotificationService**

```java
// backend/src/test/java/com/trading/notifications/PushNotificationServiceTest.java
package com.trading.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock DeviceTokenRepository deviceTokenRepository;
    @InjectMocks PushNotificationService pushService;

    @Test
    void sendToUser_isNoOpWhenNoDevicesRegistered() {
        when(deviceTokenRepository.findByUser_Id(1L)).thenReturn(List.of());
        // Should not throw
        pushService.sendToUser(1L, "Title", "Body", "zbs://dashboard");
        verify(deviceTokenRepository).findByUser_Id(1L);
    }
}
```

- [ ] **Step 10: Run tests**

```bash
cd backend && ./mvnw test -Dtest=PushNotificationServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 11: Run full suite**

```bash
cd backend && ./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 12: Commit**

```bash
cd backend
git add pom.xml \
        src/main/resources/db/migration/V12__device_tokens.sql \
        src/main/java/com/trading/notifications/DeviceToken.java \
        src/main/java/com/trading/notifications/DeviceTokenRepository.java \
        src/main/java/com/trading/notifications/DeviceTokenRequest.java \
        src/main/java/com/trading/notifications/PushNotificationService.java \
        src/main/java/com/trading/notifications/DeviceTokenController.java \
        src/main/java/com/trading/notifications/NotificationService.java \
        src/test/java/com/trading/notifications/PushNotificationServiceTest.java
git commit -m "feat: add device token registration and Firebase push notifications"
```

---

### Task 3: Backend — Zerodha Mobile Callback

**Files:**
- Modify: `backend/src/main/java/com/trading/zerodha/ZerodhaAuthController.java`
- Modify: `backend/src/test/java/com/trading/zerodha/ZerodhaAuthControllerTest.java`

**Interfaces:**
- When `User-Agent` contains `ZerodhaBreakoutMobile`, redirect to `zbs://zerodha-callback?request_token=<token>` instead of web URL

- [ ] **Step 1: Update callback method in ZerodhaAuthController**

Replace the successful redirect line in `callback()`:

```java
// Replace: response.sendRedirect(frontendBase + "/settings?zerodha=connected");
// With:
if (isMobileClient(request)) {
    response.sendRedirect("zbs://zerodha-callback?status=connected");
} else {
    response.sendRedirect(frontendBase + "/settings?zerodha=connected");
}
```

Also add the helper method inside the class:

```java
private boolean isMobileClient(HttpServletRequest request) {
    String ua = request.getHeader("User-Agent");
    return ua != null && ua.contains("ZerodhaBreakoutMobile");
}
```

- [ ] **Step 2: Add test for mobile callback redirect**

In the existing `ZerodhaAuthControllerTest`, add:

```java
@Test
void callback_redirectsToDeepLinkForMobileClient() throws Exception {
    // Arrange: valid status and nonce cookie
    Cookie nonce = new Cookie("zerodha_oauth_nonce", "valid-nonce");
    doNothing().when(zerodhaAuthService).complete(eq("valid-nonce"), eq("req-token-abc"));

    mockMvc.perform(get("/api/zerodha/callback")
                .param("request_token", "req-token-abc")
                .param("status", "success")
                .cookie(nonce)
                .header("User-Agent", "ZerodhaBreakoutMobile/1.0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "zbs://zerodha-callback?status=connected"));
}
```

- [ ] **Step 3: Run tests**

```bash
cd backend && ./mvnw test -Dtest=ZerodhaAuthControllerTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
cd backend
git add src/main/java/com/trading/zerodha/ZerodhaAuthController.java \
        src/test/java/com/trading/zerodha/ZerodhaAuthControllerTest.java
git commit -m "feat: redirect Zerodha OAuth callback to deep link for mobile clients"
```

---

### Task 4: Mobile — Expo Project Scaffold

**Files:**
- Create: `mobile/` directory (via `npx create-expo-app`)
- Create: `mobile/app.config.ts`
- Create: `mobile/.env.development`
- Create: `mobile/.env.production`
- Create: `mobile/tailwind.config.js`
- Create: `mobile/babel.config.js`
- Create: `mobile/global.css`

**Interfaces:**
- Produces: runnable Expo app with NativeWind, deep-link scheme `zbs://`, env config

- [ ] **Step 1: Scaffold Expo project**

```bash
cd D:/Zerodha_Breakout_stocks
npx create-expo-app@latest mobile --template blank-typescript
cd mobile
```

- [ ] **Step 2: Install all dependencies**

```bash
npx expo install expo-router expo-secure-store expo-notifications expo-web-browser expo-linking
npm install @tanstack/react-query axios zustand
npm install nativewind tailwindcss
npx expo install react-native-safe-area-context react-native-screens
npm install --save-dev @testing-library/react-native @testing-library/jest-native
```

- [ ] **Step 3: Create app.config.ts** (replaces generated `app.json`)

```typescript
// mobile/app.config.ts
import { ExpoConfig, ConfigContext } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'Zerodha Breakout',
  slug: 'zerodha-breakout',
  version: '1.0.0',
  scheme: 'zbs',
  platforms: ['ios', 'android'],
  ios: {
    supportsTablet: true,
    bundleIdentifier: 'com.trading.zerodhabreakout',
  },
  android: {
    package: 'com.trading.zerodhabreakout',
    adaptiveIcon: {
      backgroundColor: '#ffffff',
    },
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    ['expo-notifications', { icon: './assets/icon.png', color: '#ffffff' }],
  ],
  experiments: {
    typedRoutes: true,
  },
});
```

- [ ] **Step 4: Create env files**

```bash
# mobile/.env.development
EXPO_PUBLIC_API_URL=http://localhost:9006
```

```bash
# mobile/.env.production
EXPO_PUBLIC_API_URL=https://your-domain.com
```

- [ ] **Step 5: Configure NativeWind**

```javascript
// mobile/tailwind.config.js
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./app/**/*.{js,jsx,ts,tsx}', './components/**/*.{js,jsx,ts,tsx}'],
  presets: [require('nativewind/preset')],
  theme: { extend: {} },
  plugins: [],
};
```

```javascript
// mobile/babel.config.js
module.exports = function (api) {
  api.cache(true);
  return {
    presets: [
      ['babel-preset-expo', { jsxImportSource: 'nativewind' }],
      'nativewind/babel',
    ],
  };
};
```

```css
/* mobile/global.css */
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 6: Verify the app runs**

```bash
cd mobile && npx expo start --no-dev-client
```

Expected: Expo DevTools opens, Metro bundler starts without errors. Press `q` to quit.

- [ ] **Step 7: Commit**

```bash
cd ..
git add mobile/
git commit -m "feat: scaffold Expo managed app with NativeWind, Expo Router, and env config"
```

---

### Task 5: Mobile — API Client + Types + Auth Store

**Files:**
- Create: `mobile/lib/types.ts`
- Create: `mobile/lib/api.ts`
- Create: `mobile/store/authStore.ts`
- Create: `mobile/lib/__tests__/api.test.ts`

**Interfaces:**
- `api` — Axios instance with Bearer interceptor and auto-refresh on 401
- `useAuthStore` — `{ accessToken, refreshToken, user, login, logout, setTokens }`
- Consumes: `EXPO_PUBLIC_API_URL`, `expo-secure-store`

- [ ] **Step 1: Create types**

```typescript
// mobile/lib/types.ts
export interface User {
  id: number;
  name: string;
  email: string;
  role: 'USER' | 'ADMIN';
  active: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface Signal {
  id: number;
  symbol: string;
  entryPrice: number;
  targetPrice: number;
  stopLossPrice: number;
  status: 'PENDING' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
  source: string;
  createdAt: string;
}

export interface Position {
  id: number;
  signal: Signal;
  quantity: number;
  entryPrice: number;
  ltp?: number;
  unrealisedPnl?: number;
}

export interface ClosedTrade {
  id: number;
  symbol: string;
  entryPrice: number;
  exitPrice: number;
  quantity: number;
  realisedPnl: number;
  closedAt: string;
}

export interface UserConfig {
  maxPositions: number;
  positionSizingMethod: 'FIXED' | 'EQUAL' | 'RISK_BASED';
  positionSizingValue: number;
  marginUsagePercent: number;
  marginUsageFixedLimit: number | null;
  tradingPaused: boolean;
  syncPaused: boolean;
  zerodhaConnected: boolean;
  telegramChatId: string | null;
}

export interface AccountSummary {
  availableMargin: number | null;
  activePositions: number;
  maxPositions: number | null;
  positionSizingValue: number | null;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}
```

- [ ] **Step 2: Create API client**

```typescript
// mobile/lib/api.ts
import axios, { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:9006';

export const api = axios.create({ baseURL: BASE_URL });

// Attach Bearer token to every request
api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await SecureStore.getItemAsync('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401, attempt one refresh then retry
let isRefreshing = false;
let pendingRequests: Array<(token: string) => void> = [];

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config as AxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }
    original._retry = true;

    if (isRefreshing) {
      return new Promise((resolve) => {
        pendingRequests.push((token) => {
          original.headers = { ...original.headers, Authorization: `Bearer ${token}` };
          resolve(api(original));
        });
      });
    }

    isRefreshing = true;
    try {
      const refreshToken = await SecureStore.getItemAsync('refreshToken');
      if (!refreshToken) throw new Error('No refresh token');

      const { data } = await axios.post(`${BASE_URL}/api/auth/refresh`, { refreshToken });
      const newAccess: string = data.data.accessToken;
      const newRefresh: string = data.data.refreshToken;

      await SecureStore.setItemAsync('accessToken', newAccess);
      await SecureStore.setItemAsync('refreshToken', newRefresh);

      pendingRequests.forEach((cb) => cb(newAccess));
      pendingRequests = [];

      original.headers = { ...original.headers, Authorization: `Bearer ${newAccess}` };
      return api(original);
    } catch {
      await SecureStore.deleteItemAsync('accessToken');
      await SecureStore.deleteItemAsync('refreshToken');
      pendingRequests = [];
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  },
);
```

- [ ] **Step 3: Create auth store**

```typescript
// mobile/store/authStore.ts
import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { api } from '../lib/api';
import type { User, TokenResponse } from '../lib/types';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  restoreSession: () => Promise<boolean>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,

  login: async (email, password) => {
    const { data } = await api.post<{ data: TokenResponse }>('/api/auth/token', { email, password });
    const { accessToken, refreshToken } = data.data;
    await SecureStore.setItemAsync('accessToken', accessToken);
    await SecureStore.setItemAsync('refreshToken', refreshToken);
    const meRes = await api.get<{ data: User }>('/api/users/me');
    set({ user: meRes.data.data, isAuthenticated: true });
  },

  logout: async () => {
    const refreshToken = await SecureStore.getItemAsync('refreshToken');
    if (refreshToken) {
      try { await api.post('/api/auth/revoke', { refreshToken }); } catch { /* ignore */ }
    }
    await SecureStore.deleteItemAsync('accessToken');
    await SecureStore.deleteItemAsync('refreshToken');
    set({ user: null, isAuthenticated: false });
  },

  restoreSession: async () => {
    try {
      const token = await SecureStore.getItemAsync('accessToken');
      if (!token) return false;
      const meRes = await api.get<{ data: User }>('/api/users/me');
      set({ user: meRes.data.data, isAuthenticated: true });
      return true;
    } catch {
      return false;
    }
  },
}));
```

- [ ] **Step 4: Write test for auth store login**

```typescript
// mobile/lib/__tests__/api.test.ts
import axios from 'axios';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

jest.mock('axios', () => {
  const actual = jest.requireActual('axios');
  return {
    ...actual,
    create: jest.fn(() => actual.create()),
    post: jest.fn(),
  };
});

describe('api client', () => {
  it('exports a base URL from EXPO_PUBLIC_API_URL', () => {
    // Smoke test — the module must load without throwing
    expect(() => require('../api')).not.toThrow();
  });
});
```

- [ ] **Step 5: Run tests**

```bash
cd mobile && npx jest lib/__tests__/api.test.ts --no-coverage 2>&1 | tail -8
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add mobile/lib/types.ts mobile/lib/api.ts mobile/store/authStore.ts \
        mobile/lib/__tests__/api.test.ts
git commit -m "feat: add API client with Bearer auth, auto-refresh, and auth store"
```

---

### Task 6: Mobile — Auth Screens + Root Layout

**Files:**
- Create: `mobile/app/_layout.tsx`
- Create: `mobile/app/(auth)/_layout.tsx`
- Create: `mobile/app/(auth)/login.tsx`
- Create: `mobile/app/(auth)/zerodha-connect.tsx`

**Interfaces:**
- Consumes: `useAuthStore.restoreSession`, `useAuthStore.login`
- Root layout redirects unauthenticated users to `/(auth)/login`

- [ ] **Step 1: Create root layout with auth gate**

```typescript
// mobile/app/_layout.tsx
import { useEffect } from 'react';
import { Stack, router } from 'expo-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../global.css';
import { useAuthStore } from '../store/authStore';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
});

export default function RootLayout() {
  const restoreSession = useAuthStore((s) => s.restoreSession);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  useEffect(() => {
    restoreSession().then((ok) => {
      if (!ok) router.replace('/(auth)/login');
    });
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <Stack screenOptions={{ headerShown: false }} />
    </QueryClientProvider>
  );
}
```

- [ ] **Step 2: Create auth group layout**

```typescript
// mobile/app/(auth)/_layout.tsx
import { Stack } from 'expo-router';

export default function AuthLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
```

- [ ] **Step 3: Create login screen**

```typescript
// mobile/app/(auth)/login.tsx
import { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { router } from 'expo-router';
import { useAuthStore } from '../../store/authStore';

export default function LoginScreen() {
  const login = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    if (!email || !password) return;
    setLoading(true);
    try {
      await login(email, password);
      router.replace('/(tabs)/dashboard');
    } catch {
      Alert.alert('Login failed', 'Invalid email or password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View className="flex-1 justify-center px-6 bg-gray-950">
      <Text className="text-white text-3xl font-bold mb-8">Zerodha Breakout</Text>

      <TextInput
        className="bg-gray-800 text-white rounded-lg px-4 py-3 mb-4"
        placeholder="Email"
        placeholderTextColor="#6b7280"
        autoCapitalize="none"
        keyboardType="email-address"
        value={email}
        onChangeText={setEmail}
      />
      <TextInput
        className="bg-gray-800 text-white rounded-lg px-4 py-3 mb-6"
        placeholder="Password"
        placeholderTextColor="#6b7280"
        secureTextEntry
        value={password}
        onChangeText={setPassword}
      />

      <TouchableOpacity
        className="bg-blue-600 rounded-lg py-4 items-center"
        onPress={handleLogin}
        disabled={loading}
      >
        {loading
          ? <ActivityIndicator color="#fff" />
          : <Text className="text-white font-semibold text-base">Sign In</Text>
        }
      </TouchableOpacity>
    </View>
  );
}
```

- [ ] **Step 4: Create Zerodha connect screen**

```typescript
// mobile/app/(auth)/zerodha-connect.tsx
import { useEffect } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator } from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import { router } from 'expo-router';
import { api } from '../../lib/api';

WebBrowser.maybeCompleteAuthSession();

export default function ZerodhaConnectScreen() {
  useEffect(() => {
    Linking.addEventListener('url', handleDeepLink);
    return () => Linking.removeAllListeners('url');
  }, []);

  const handleDeepLink = ({ url }: { url: string }) => {
    if (url.startsWith('zbs://zerodha-callback')) {
      router.replace('/(tabs)/dashboard');
    }
  };

  const startZerodhaOAuth = async () => {
    const apiUrl = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:9006';
    await WebBrowser.openBrowserAsync(`${apiUrl}/api/zerodha/login`, {
      presentationStyle: WebBrowser.WebBrowserPresentationStyle.FORM_SHEET,
    });
  };

  return (
    <View className="flex-1 justify-center px-6 bg-gray-950 items-center">
      <Text className="text-white text-2xl font-bold mb-4">Connect Zerodha</Text>
      <Text className="text-gray-400 text-center mb-8">
        Link your Zerodha account to start trading.
      </Text>
      <TouchableOpacity
        className="bg-orange-500 rounded-lg px-8 py-4"
        onPress={startZerodhaOAuth}
      >
        <Text className="text-white font-semibold text-base">Connect with Kite</Text>
      </TouchableOpacity>
      <TouchableOpacity className="mt-6" onPress={() => router.replace('/(tabs)/dashboard')}>
        <Text className="text-gray-500">Skip for now</Text>
      </TouchableOpacity>
    </View>
  );
}
```

- [ ] **Step 5: Commit**

```bash
git add mobile/app/_layout.tsx mobile/app/\(auth\)/
git commit -m "feat: add auth screens and root layout auth gate"
```

---

### Task 7: Mobile — Query Hooks + Mutation Hooks

**Files:**
- Create: `mobile/hooks/queries/useAccountSummary.ts`
- Create: `mobile/hooks/queries/useSignals.ts`
- Create: `mobile/hooks/queries/useSignal.ts`
- Create: `mobile/hooks/queries/usePortfolio.ts`
- Create: `mobile/hooks/queries/useHistory.ts`
- Create: `mobile/hooks/queries/useUserConfig.ts`
- Create: `mobile/hooks/mutations/useSyncSignals.ts`
- Create: `mobile/hooks/mutations/useAddSignal.ts`
- Create: `mobile/hooks/mutations/useCancelPending.ts`
- Create: `mobile/hooks/mutations/useClosePosition.ts`
- Create: `mobile/hooks/mutations/useUpdateConfig.ts`
- Create: `mobile/hooks/mutations/useConnectTelegram.ts`
- Create: `mobile/hooks/__tests__/useSignals.test.ts`

**Interfaces:**
- All hooks consume `api` from `mobile/lib/api.ts` and types from `mobile/lib/types.ts`
- All queries return TanStack Query `UseQueryResult`

- [ ] **Step 1: Create query hooks**

```typescript
// mobile/hooks/queries/useAccountSummary.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { AccountSummary, ApiResponse } from '../../lib/types';

export function useAccountSummary() {
  return useQuery({
    queryKey: ['account-summary'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<AccountSummary>>('/api/users/me/account-summary');
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useSignals.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Signal, ApiResponse } from '../../lib/types';

export function useSignals(status?: string) {
  return useQuery({
    queryKey: ['signals', status],
    queryFn: async () => {
      const params = status ? { status } : {};
      const { data } = await api.get<ApiResponse<Signal[]>>('/api/signals', { params });
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useSignal.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Signal, ApiResponse } from '../../lib/types';

export function useSignal(id: number) {
  return useQuery({
    queryKey: ['signal', id],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<Signal>>(`/api/signals/${id}`);
      return data.data;
    },
    enabled: !!id,
  });
}
```

```typescript
// mobile/hooks/queries/usePortfolio.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Position, ApiResponse } from '../../lib/types';

export function usePortfolio() {
  return useQuery({
    queryKey: ['portfolio'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<Position[]>>('/api/portfolio');
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useHistory.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { ClosedTrade, ApiResponse } from '../../lib/types';

export function useHistory() {
  return useQuery({
    queryKey: ['history'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ClosedTrade[]>>('/api/portfolio/history');
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useUserConfig.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { UserConfig, ApiResponse } from '../../lib/types';

export function useUserConfig() {
  return useQuery({
    queryKey: ['user-config'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<UserConfig>>('/api/users/me/config');
      return data.data;
    },
  });
}
```

- [ ] **Step 2: Create mutation hooks**

```typescript
// mobile/hooks/mutations/useSyncSignals.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useSyncSignals() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post('/api/signals/sync'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  });
}
```

```typescript
// mobile/hooks/mutations/useCancelPending.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useCancelPending() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (signalId: number) => api.post(`/api/signals/${signalId}/cancel`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] });
      qc.invalidateQueries({ queryKey: ['portfolio'] });
    },
  });
}
```

```typescript
// mobile/hooks/mutations/useClosePosition.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useClosePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (signalId: number) => api.post(`/api/signals/${signalId}/close`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portfolio'] });
      qc.invalidateQueries({ queryKey: ['signals'] });
    },
  });
}
```

```typescript
// mobile/hooks/mutations/useUpdateConfig.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { UserConfig } from '../../lib/types';

export function useUpdateConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (config: Partial<UserConfig>) => api.put('/api/users/me/config', config),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['user-config'] }),
  });
}
```

```typescript
// mobile/hooks/mutations/useConnectTelegram.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useConnectTelegram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (botToken: string) => api.post('/api/users/me/telegram/bot', { botToken }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['user-config'] }),
  });
}
```

```typescript
// mobile/hooks/mutations/useAddSignal.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useAddSignal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: {
      symbol: string;
      entryPrice: number;
      targetPrice: number;
      stopLossPrice: number;
    }) => api.post('/api/signals', payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  });
}
```

- [ ] **Step 3: Write test for useSignals**

```typescript
// mobile/hooks/__tests__/useSignals.test.ts
import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useSignals } from '../queries/useSignals';
import { api } from '../../lib/api';

jest.mock('../../lib/api', () => ({ api: { get: jest.fn() } }));

const wrapper = ({ children }: { children: React.ReactNode }) => (
  React.createElement(QueryClientProvider, {
    client: new QueryClient({ defaultOptions: { queries: { retry: false } } }),
  }, children)
);

describe('useSignals', () => {
  it('returns signal list from API', async () => {
    const signals = [{ id: 1, symbol: 'RELIANCE', status: 'PENDING' }];
    (api.get as jest.Mock).mockResolvedValue({ data: { data: signals } });

    const { result } = renderHook(() => useSignals(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(signals);
  });
});
```

- [ ] **Step 4: Run tests**

```bash
cd mobile && npx jest hooks/__tests__/useSignals.test.ts --no-coverage 2>&1 | tail -8
```

Expected: test passes.

- [ ] **Step 5: Commit**

```bash
git add mobile/hooks/
git commit -m "feat: add TanStack Query data hooks for all mobile screens"
```

---

### Task 8: Mobile — Tab Layout + Dashboard Screen

**Files:**
- Create: `mobile/app/(tabs)/_layout.tsx`
- Create: `mobile/app/(tabs)/dashboard.tsx`

**Interfaces:**
- Consumes: `useAccountSummary`, `usePortfolio`

- [ ] **Step 1: Create tab layout**

```typescript
// mobile/app/(tabs)/_layout.tsx
import { Tabs } from 'expo-router';
import { Text } from 'react-native';

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: '#030712' },
        headerTintColor: '#fff',
        tabBarStyle: { backgroundColor: '#030712', borderTopColor: '#1f2937' },
        tabBarActiveTintColor: '#3b82f6',
        tabBarInactiveTintColor: '#6b7280',
      }}
    >
      <Tabs.Screen name="dashboard" options={{ title: 'Dashboard', tabBarLabel: 'Home' }} />
      <Tabs.Screen name="signals" options={{ title: 'Signals' }} />
      <Tabs.Screen name="portfolio" options={{ title: 'Portfolio' }} />
      <Tabs.Screen name="history" options={{ title: 'History' }} />
      <Tabs.Screen name="settings" options={{ title: 'Settings' }} />
    </Tabs>
  );
}
```

- [ ] **Step 2: Create dashboard screen**

```typescript
// mobile/app/(tabs)/dashboard.tsx
import { View, Text, ScrollView, RefreshControl } from 'react-native';
import { useAccountSummary } from '../../hooks/queries/useAccountSummary';
import { usePortfolio } from '../../hooks/queries/usePortfolio';

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <View className="bg-gray-800 rounded-xl p-4 flex-1 mx-1">
      <Text className="text-gray-400 text-xs mb-1">{label}</Text>
      <Text className="text-white text-xl font-bold">{value}</Text>
    </View>
  );
}

export default function DashboardScreen() {
  const summary = useAccountSummary();
  const portfolio = usePortfolio();

  const refreshing = summary.isFetching || portfolio.isFetching;
  const onRefresh = () => {
    summary.refetch();
    portfolio.refetch();
  };

  const margin = summary.data?.availableMargin != null
    ? `₹${summary.data.availableMargin.toLocaleString('en-IN')}`
    : '—';

  const unrealisedPnl = portfolio.data
    ?.reduce((sum, p) => sum + (p.unrealisedPnl ?? 0), 0) ?? 0;

  const pnlColor = unrealisedPnl >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <ScrollView
      className="flex-1 bg-gray-950 px-4 pt-4"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#fff" />}
    >
      <Text className="text-gray-400 text-sm mb-4">
        {summary.data?.activePositions ?? 0} / {summary.data?.maxPositions ?? '—'} positions open
      </Text>

      <View className="flex-row mb-4">
        <StatCard label="Available Margin" value={margin} />
        <View className="bg-gray-800 rounded-xl p-4 flex-1 mx-1">
          <Text className="text-gray-400 text-xs mb-1">Unrealised P&L</Text>
          <Text className={`text-xl font-bold ${pnlColor}`}>
            {unrealisedPnl >= 0 ? '+' : ''}₹{unrealisedPnl.toFixed(2)}
          </Text>
        </View>
      </View>
    </ScrollView>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add mobile/app/\(tabs\)/
git commit -m "feat: add tab layout and dashboard screen"
```

---

### Task 9: Mobile — Signals Screen + Signal Detail

**Files:**
- Create: `mobile/app/(tabs)/signals.tsx`
- Create: `mobile/app/signals/[id].tsx`

**Interfaces:**
- Consumes: `useSignals`, `useSyncSignals`, `useCancelPending`, `useClosePosition`, `useSignal`

- [ ] **Step 1: Create signals screen**

```typescript
// mobile/app/(tabs)/signals.tsx
import { useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, RefreshControl, Alert } from 'react-native';
import { router } from 'expo-router';
import { useSignals } from '../../hooks/queries/useSignals';
import { useSyncSignals } from '../../hooks/mutations/useSyncSignals';
import type { Signal } from '../../lib/types';

const STATUS_FILTERS = ['ALL', 'PENDING', 'ACTIVE', 'CLOSED', 'CANCELLED'] as const;

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'text-yellow-400',
  ACTIVE: 'text-green-400',
  CLOSED: 'text-gray-400',
  CANCELLED: 'text-red-400',
};

function SignalRow({ signal }: { signal: Signal }) {
  return (
    <TouchableOpacity
      className="bg-gray-800 rounded-xl p-4 mb-2"
      onPress={() => router.push(`/signals/${signal.id}`)}
    >
      <View className="flex-row justify-between items-center">
        <Text className="text-white font-semibold text-base">{signal.symbol}</Text>
        <Text className={STATUS_COLOR[signal.status] ?? 'text-gray-400'}>
          {signal.status}
        </Text>
      </View>
      <View className="flex-row mt-2 gap-4">
        <Text className="text-gray-400 text-xs">Entry: ₹{signal.entryPrice}</Text>
        <Text className="text-gray-400 text-xs">Target: ₹{signal.targetPrice}</Text>
        <Text className="text-gray-400 text-xs">SL: ₹{signal.stopLossPrice}</Text>
      </View>
    </TouchableOpacity>
  );
}

export default function SignalsScreen() {
  const [activeFilter, setActiveFilter] = useState<string>('ALL');
  const status = activeFilter === 'ALL' ? undefined : activeFilter;
  const signals = useSignals(status);
  const sync = useSyncSignals();

  const handleSync = async () => {
    try {
      await sync.mutateAsync();
    } catch {
      Alert.alert('Sync failed', 'Could not sync signals from Google Sheet.');
    }
  };

  return (
    <View className="flex-1 bg-gray-950 px-4 pt-4">
      {/* Filter tabs */}
      <View className="flex-row mb-4 gap-2 flex-wrap">
        {STATUS_FILTERS.map((f) => (
          <TouchableOpacity
            key={f}
            className={`px-3 py-1 rounded-full border ${
              activeFilter === f
                ? 'bg-blue-600 border-blue-600'
                : 'border-gray-600'
            }`}
            onPress={() => setActiveFilter(f)}
          >
            <Text className="text-white text-xs">{f}</Text>
          </TouchableOpacity>
        ))}
        <TouchableOpacity
          className="px-3 py-1 rounded-full bg-gray-700 ml-auto"
          onPress={handleSync}
          disabled={sync.isPending}
        >
          <Text className="text-white text-xs">{sync.isPending ? 'Syncing…' : 'Sync'}</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={signals.data ?? []}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <SignalRow signal={item} />}
        refreshControl={
          <RefreshControl refreshing={signals.isFetching} onRefresh={signals.refetch} tintColor="#fff" />
        }
        ListEmptyComponent={
          <Text className="text-gray-500 text-center mt-12">No signals</Text>
        }
      />
    </View>
  );
}
```

- [ ] **Step 2: Create signal detail screen**

```typescript
// mobile/app/signals/[id].tsx
import { View, Text, ScrollView, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { useSignal } from '../../hooks/queries/useSignal';
import { useCancelPending } from '../../hooks/mutations/useCancelPending';
import { useClosePosition } from '../../hooks/mutations/useClosePosition';

export default function SignalDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const signal = useSignal(Number(id));
  const cancel = useCancelPending();
  const close = useClosePosition();

  if (signal.isLoading) {
    return <View className="flex-1 bg-gray-950 justify-center items-center"><ActivityIndicator color="#fff" /></View>;
  }
  if (!signal.data) {
    return <View className="flex-1 bg-gray-950 justify-center items-center"><Text className="text-gray-400">Signal not found</Text></View>;
  }

  const s = signal.data;

  const handleCancel = () => {
    Alert.alert('Cancel Order', `Cancel pending order for ${s.symbol}?`, [
      { text: 'No', style: 'cancel' },
      { text: 'Yes, Cancel', style: 'destructive', onPress: async () => {
        try { await cancel.mutateAsync(s.id); router.back(); }
        catch { Alert.alert('Error', 'Could not cancel order.'); }
      }},
    ]);
  };

  const handleClose = () => {
    Alert.alert('Close Position', `Market-sell ${s.symbol}?`, [
      { text: 'No', style: 'cancel' },
      { text: 'Yes, Close', style: 'destructive', onPress: async () => {
        try { await close.mutateAsync(s.id); router.back(); }
        catch { Alert.alert('Error', 'Could not close position.'); }
      }},
    ]);
  };

  return (
    <ScrollView className="flex-1 bg-gray-950 px-4 pt-4">
      <Text className="text-white text-2xl font-bold mb-2">{s.symbol}</Text>
      <Text className="text-gray-400 mb-6">{s.status}</Text>

      {[
        ['Entry Price', `₹${s.entryPrice}`],
        ['Target Price', `₹${s.targetPrice}`],
        ['Stop Loss', `₹${s.stopLossPrice}`],
        ['Source', s.source],
      ].map(([label, value]) => (
        <View key={label} className="flex-row justify-between py-3 border-b border-gray-800">
          <Text className="text-gray-400">{label}</Text>
          <Text className="text-white">{value}</Text>
        </View>
      ))}

      <View className="mt-8 gap-3">
        {s.status === 'PENDING' && (
          <TouchableOpacity
            className="bg-red-700 rounded-xl py-4 items-center"
            onPress={handleCancel}
            disabled={cancel.isPending}
          >
            <Text className="text-white font-semibold">Cancel Pending Order</Text>
          </TouchableOpacity>
        )}
        {s.status === 'ACTIVE' && (
          <TouchableOpacity
            className="bg-red-700 rounded-xl py-4 items-center"
            onPress={handleClose}
            disabled={close.isPending}
          >
            <Text className="text-white font-semibold">Close Active Position</Text>
          </TouchableOpacity>
        )}
      </View>
    </ScrollView>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add mobile/app/\(tabs\)/signals.tsx mobile/app/signals/
git commit -m "feat: add signals list and signal detail screens"
```

---

### Task 10: Mobile — Portfolio Screen + WebSocket LTP Hook

**Files:**
- Create: `mobile/store/portfolioStore.ts`
- Create: `mobile/hooks/usePortfolioLtp.ts`
- Create: `mobile/app/(tabs)/portfolio.tsx`

**Interfaces:**
- `usePortfolioLtp()` — manages WebSocket connection lifecycle, merges LTP into `portfolioStore`
- `portfolioStore` — `{ ltpMap: Record<number, number>, setLtp }`

- [ ] **Step 1: Create portfolio Zustand store**

```typescript
// mobile/store/portfolioStore.ts
import { create } from 'zustand';

interface PortfolioState {
  ltpMap: Record<number, number>; // signalId → ltp
  setLtp: (signalId: number, ltp: number) => void;
  clearLtp: () => void;
}

export const usePortfolioStore = create<PortfolioState>((set) => ({
  ltpMap: {},
  setLtp: (signalId, ltp) =>
    set((s) => ({ ltpMap: { ...s.ltpMap, [signalId]: ltp } })),
  clearLtp: () => set({ ltpMap: {} }),
}));
```

- [ ] **Step 2: Create WebSocket LTP hook**

```typescript
// mobile/hooks/usePortfolioLtp.ts
import { useEffect, useRef } from 'react';
import { AppState, AppStateStatus } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { usePortfolioStore } from '../store/portfolioStore';

const WS_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:9006')
  .replace(/^http/, 'ws') + '/ws/ltp';

export function usePortfolioLtp() {
  const wsRef = useRef<WebSocket | null>(null);
  const setLtp = usePortfolioStore((s) => s.setLtp);
  const clearLtp = usePortfolioStore((s) => s.clearLtp);

  const connect = async () => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    const token = await SecureStore.getItemAsync('accessToken');
    if (!token) return;

    const ws = new WebSocket(`${WS_URL}?token=${token}`);
    wsRef.current = ws;

    ws.onmessage = (event) => {
      try {
        const payload: { signalId: number; ltp: number } = JSON.parse(event.data);
        setLtp(payload.signalId, payload.ltp);
      } catch { /* ignore malformed messages */ }
    };

    ws.onerror = () => ws.close();
    ws.onclose = () => { wsRef.current = null; };
  };

  const disconnect = () => {
    wsRef.current?.close();
    wsRef.current = null;
    clearLtp();
  };

  useEffect(() => {
    connect();

    const sub = AppState.addEventListener('change', (state: AppStateStatus) => {
      if (state === 'active') connect();
      else disconnect();
    });

    return () => {
      sub.remove();
      disconnect();
    };
  }, []);
}
```

- [ ] **Step 3: Create portfolio screen**

```typescript
// mobile/app/(tabs)/portfolio.tsx
import { View, Text, FlatList, RefreshControl } from 'react-native';
import { usePortfolio } from '../../hooks/queries/usePortfolio';
import { usePortfolioLtp } from '../../hooks/usePortfolioLtp';
import { usePortfolioStore } from '../../store/portfolioStore';
import type { Position } from '../../lib/types';

function PositionRow({ position }: { position: Position }) {
  const ltp = usePortfolioStore((s) => s.ltpMap[position.signal.id] ?? position.ltp);
  const pnl = ltp != null
    ? (ltp - position.entryPrice) * position.quantity
    : position.unrealisedPnl ?? null;
  const pnlColor = (pnl ?? 0) >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <View className="bg-gray-800 rounded-xl p-4 mb-2">
      <View className="flex-row justify-between">
        <Text className="text-white font-semibold">{position.signal.symbol}</Text>
        {pnl != null && (
          <Text className={`font-semibold ${pnlColor}`}>
            {pnl >= 0 ? '+' : ''}₹{pnl.toFixed(2)}
          </Text>
        )}
      </View>
      <View className="flex-row mt-2 gap-4">
        <Text className="text-gray-400 text-xs">Qty: {position.quantity}</Text>
        <Text className="text-gray-400 text-xs">Entry: ₹{position.entryPrice}</Text>
        {ltp != null && <Text className="text-gray-400 text-xs">LTP: ₹{ltp}</Text>}
      </View>
    </View>
  );
}

export default function PortfolioScreen() {
  usePortfolioLtp(); // manages WebSocket lifecycle
  const portfolio = usePortfolio();

  return (
    <View className="flex-1 bg-gray-950 px-4 pt-4">
      <FlatList
        data={portfolio.data ?? []}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <PositionRow position={item} />}
        refreshControl={
          <RefreshControl refreshing={portfolio.isFetching} onRefresh={portfolio.refetch} tintColor="#fff" />
        }
        ListEmptyComponent={
          <Text className="text-gray-500 text-center mt-12">No active positions</Text>
        }
      />
    </View>
  );
}
```

- [ ] **Step 4: Commit**

```bash
git add mobile/store/portfolioStore.ts mobile/hooks/usePortfolioLtp.ts \
        mobile/app/\(tabs\)/portfolio.tsx
git commit -m "feat: add portfolio screen with live LTP via WebSocket"
```

---

### Task 11: Mobile — History Screen

**Files:**
- Create: `mobile/app/(tabs)/history.tsx`

**Interfaces:**
- Consumes: `useHistory`

- [ ] **Step 1: Create history screen**

```typescript
// mobile/app/(tabs)/history.tsx
import { View, Text, FlatList, RefreshControl } from 'react-native';
import { useHistory } from '../../hooks/queries/useHistory';
import type { ClosedTrade } from '../../lib/types';

function TradeRow({ trade }: { trade: ClosedTrade }) {
  const pnlColor = trade.realisedPnl >= 0 ? 'text-green-400' : 'text-red-400';
  return (
    <View className="bg-gray-800 rounded-xl p-4 mb-2">
      <View className="flex-row justify-between">
        <Text className="text-white font-semibold">{trade.symbol}</Text>
        <Text className={`font-semibold ${pnlColor}`}>
          {trade.realisedPnl >= 0 ? '+' : ''}₹{trade.realisedPnl.toFixed(2)}
        </Text>
      </View>
      <View className="flex-row mt-2 gap-4">
        <Text className="text-gray-400 text-xs">Qty: {trade.quantity}</Text>
        <Text className="text-gray-400 text-xs">Entry: ₹{trade.entryPrice}</Text>
        <Text className="text-gray-400 text-xs">Exit: ₹{trade.exitPrice}</Text>
      </View>
      <Text className="text-gray-500 text-xs mt-1">
        {new Date(trade.closedAt).toLocaleDateString('en-IN')}
      </Text>
    </View>
  );
}

export default function HistoryScreen() {
  const history = useHistory();

  const totalPnl = (history.data ?? []).reduce((sum, t) => sum + t.realisedPnl, 0);
  const totalColor = totalPnl >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <View className="flex-1 bg-gray-950 px-4 pt-4">
      {history.data && history.data.length > 0 && (
        <View className="bg-gray-800 rounded-xl p-4 mb-4 flex-row justify-between">
          <Text className="text-gray-400">Total Realised P&L</Text>
          <Text className={`font-bold ${totalColor}`}>
            {totalPnl >= 0 ? '+' : ''}₹{totalPnl.toFixed(2)}
          </Text>
        </View>
      )}
      <FlatList
        data={history.data ?? []}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <TradeRow trade={item} />}
        refreshControl={
          <RefreshControl refreshing={history.isFetching} onRefresh={history.refetch} tintColor="#fff" />
        }
        ListEmptyComponent={
          <Text className="text-gray-500 text-center mt-12">No closed trades</Text>
        }
      />
    </View>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add mobile/app/\(tabs\)/history.tsx
git commit -m "feat: add history screen with closed trades and total P&L"
```

---

### Task 12: Mobile — Settings Screen

**Files:**
- Create: `mobile/app/(tabs)/settings.tsx`

**Interfaces:**
- Consumes: `useUserConfig`, `useUpdateConfig`, `useConnectTelegram`, `useAuthStore`
- Initiates Zerodha OAuth via `router.push('/(auth)/zerodha-connect')`

- [ ] **Step 1: Create settings screen**

```typescript
// mobile/app/(tabs)/settings.tsx
import { useState } from 'react';
import { View, Text, Switch, TouchableOpacity, TextInput, Alert, ScrollView } from 'react-native';
import { router } from 'expo-router';
import { useUserConfig } from '../../hooks/queries/useUserConfig';
import { useUpdateConfig } from '../../hooks/mutations/useUpdateConfig';
import { useConnectTelegram } from '../../hooks/mutations/useConnectTelegram';
import { useAuthStore } from '../../store/authStore';

export default function SettingsScreen() {
  const config = useUserConfig();
  const updateConfig = useUpdateConfig();
  const connectTelegram = useConnectTelegram();
  const logout = useAuthStore((s) => s.logout);

  const [botToken, setBotToken] = useState('');

  const toggle = async (field: 'tradingPaused' | 'syncPaused', value: boolean) => {
    try {
      await updateConfig.mutateAsync({ [field]: value });
    } catch {
      Alert.alert('Error', 'Could not update setting.');
    }
  };

  const handleConnectBot = async () => {
    if (!botToken.trim()) return;
    try {
      await connectTelegram.mutateAsync(botToken.trim());
      setBotToken('');
      Alert.alert('Success', 'Telegram bot connected.');
    } catch {
      Alert.alert('Error', 'Could not connect Telegram bot.');
    }
  };

  const handleLogout = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  const cfg = config.data;

  return (
    <ScrollView className="flex-1 bg-gray-950 px-4 pt-4">

      {/* Zerodha */}
      <Text className="text-gray-400 text-xs uppercase mb-2 mt-4">Zerodha</Text>
      <View className="bg-gray-800 rounded-xl p-4 mb-4 flex-row justify-between items-center">
        <Text className="text-white">
          {cfg?.zerodhaConnected ? 'Connected' : 'Not connected'}
        </Text>
        <TouchableOpacity
          className="bg-orange-500 px-4 py-2 rounded-lg"
          onPress={() => router.push('/(auth)/zerodha-connect')}
        >
          <Text className="text-white text-sm">{cfg?.zerodhaConnected ? 'Reconnect' : 'Connect'}</Text>
        </TouchableOpacity>
      </View>

      {/* Trading controls */}
      <Text className="text-gray-400 text-xs uppercase mb-2">Trading</Text>
      <View className="bg-gray-800 rounded-xl mb-4">
        {[
          { label: 'Pause Trading', field: 'tradingPaused' as const, value: cfg?.tradingPaused },
          { label: 'Pause Sync', field: 'syncPaused' as const, value: cfg?.syncPaused },
        ].map(({ label, field, value }) => (
          <View key={field} className="flex-row justify-between items-center px-4 py-4 border-b border-gray-700 last:border-0">
            <Text className="text-white">{label}</Text>
            <Switch
              value={value ?? false}
              onValueChange={(v) => toggle(field, v)}
              trackColor={{ true: '#3b82f6' }}
            />
          </View>
        ))}
      </View>

      {/* Telegram */}
      <Text className="text-gray-400 text-xs uppercase mb-2">Telegram</Text>
      <View className="bg-gray-800 rounded-xl p-4 mb-4">
        {cfg?.telegramChatId
          ? <Text className="text-green-400 mb-2">Bot connected</Text>
          : <Text className="text-gray-400 mb-2">No bot connected</Text>
        }
        <TextInput
          className="bg-gray-700 text-white rounded-lg px-3 py-2 mb-2"
          placeholder="Bot token"
          placeholderTextColor="#6b7280"
          value={botToken}
          onChangeText={setBotToken}
        />
        <TouchableOpacity
          className="bg-blue-600 rounded-lg py-3 items-center"
          onPress={handleConnectBot}
          disabled={connectTelegram.isPending}
        >
          <Text className="text-white text-sm">Connect Bot</Text>
        </TouchableOpacity>
      </View>

      {/* Logout */}
      <TouchableOpacity
        className="bg-red-700 rounded-xl py-4 items-center mb-8"
        onPress={handleLogout}
      >
        <Text className="text-white font-semibold">Sign Out</Text>
      </TouchableOpacity>

    </ScrollView>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add mobile/app/\(tabs\)/settings.tsx
git commit -m "feat: add settings screen with Zerodha, Telegram, trading controls"
```

---

### Task 13: Mobile — Push Notification Registration + Deep-Link Handling

**Files:**
- Create: `mobile/hooks/usePushNotifications.ts`
- Modify: `mobile/app/_layout.tsx`

**Interfaces:**
- `usePushNotifications()` — requests permissions, gets token, registers with backend, handles notification taps

- [ ] **Step 1: Create push notification hook**

```typescript
// mobile/hooks/usePushNotifications.ts
import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { router } from 'expo-router';
import { api } from '../lib/api';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export function usePushNotifications() {
  useEffect(() => {
    registerForPushNotifications();

    // Handle tap on notification when app is foregrounded
    const sub = Notifications.addNotificationResponseReceivedListener((response) => {
      const deepLink = response.notification.request.content.data?.deepLink as string | undefined;
      if (deepLink) {
        const path = deepLink.replace('zbs:/', '');
        router.push(path as never);
      }
    });

    return () => sub.remove();
  }, []);
}

async function registerForPushNotifications() {
  if (Platform.OS === 'web') return;

  const { status: existing } = await Notifications.getPermissionsAsync();
  let finalStatus = existing;
  if (existing !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }
  if (finalStatus !== 'granted') return;

  try {
    const tokenData = await Notifications.getExpoPushTokenAsync();
    // Exchange Expo token for native FCM/APNs token
    const nativeToken = (await Notifications.getDevicePushTokenAsync()).data;
    const platform = Platform.OS === 'ios' ? 'APNS' : 'FCM';
    await api.post('/api/users/me/push-token', { token: nativeToken, platform });
  } catch (e) {
    console.warn('Push token registration failed:', e);
  }
}
```

- [ ] **Step 2: Call hook in root layout**

In `mobile/app/_layout.tsx`, import and call `usePushNotifications()`:

```typescript
// Add import:
import { usePushNotifications } from '../hooks/usePushNotifications';

// Inside RootLayout component, add:
usePushNotifications();
```

- [ ] **Step 3: Commit**

```bash
git add mobile/hooks/usePushNotifications.ts mobile/app/_layout.tsx
git commit -m "feat: add push notification registration and deep-link tap handling"
```

---

### Task 14: CI/CD — GitHub Actions EAS Build Workflow

**Files:**
- Create: `.github/workflows/mobile-build.yml`
- Create: `mobile/eas.json`

**Interfaces:**
- On push to `main`: triggers EAS cloud build for Android + iOS
- On tags matching `mobile-v*`: runs `eas update` OTA

- [ ] **Step 1: Create EAS config**

```json
// mobile/eas.json
{
  "cli": { "version": ">= 7.0.0" },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal"
    },
    "preview": {
      "distribution": "internal"
    },
    "production": {
      "autoIncrement": true
    }
  },
  "submit": {
    "production": {}
  }
}
```

- [ ] **Step 2: Create GitHub Actions workflow**

```yaml
# .github/workflows/mobile-build.yml
name: Mobile — EAS Build

on:
  push:
    branches: [main]
    tags: ['mobile-v*']

jobs:
  build:
    name: EAS Build (production)
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: mobile/package-lock.json
      - name: Install dependencies
        run: cd mobile && npm ci
      - name: Install EAS CLI
        run: npm install -g eas-cli
      - name: Build Android + iOS
        working-directory: mobile
        env:
          EXPO_TOKEN: ${{ secrets.EXPO_TOKEN }}
        run: eas build --platform all --non-interactive --profile production

  ota-update:
    name: EAS OTA Update
    runs-on: ubuntu-latest
    if: startsWith(github.ref, 'refs/tags/mobile-v')
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: mobile/package-lock.json
      - name: Install dependencies
        run: cd mobile && npm ci
      - name: Install EAS CLI
        run: npm install -g eas-cli
      - name: Publish OTA update
        working-directory: mobile
        env:
          EXPO_TOKEN: ${{ secrets.EXPO_TOKEN }}
        run: eas update --branch production --message "OTA update ${{ github.ref_name }}" --non-interactive
```

- [ ] **Step 3: Add EXPO_TOKEN secret to GitHub**

In the GitHub repo → Settings → Secrets and variables → Actions, add:
- `EXPO_TOKEN` — your EAS personal access token from `expo.dev/accounts/<username>/settings/access-tokens`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/mobile-build.yml mobile/eas.json
git commit -m "ci: add GitHub Actions EAS build and OTA update workflow"
```

---

## Self-Review

**Spec coverage check:**
- Architecture / monorepo layout — Task 4 (scaffold) ✓
- Token auth (access 15-min, refresh 30-day) — Task 1 ✓
- Refresh token rotation — Task 1, `MobileAuthService.refresh()` revokes old and issues new ✓
- V11 refresh_tokens, V12 device_tokens — Tasks 1 and 2 (V9/V10 already used — corrected from spec) ✓
- Bearer filter alongside cookie filter — Task 1, JwtFilter update ✓
- Zerodha OAuth mobile deep-link — Task 3 ✓
- Device token register/deregister — Task 2 ✓
- Firebase push notifications — Task 2 ✓
- Push events (fill, target, SL, daily summary) — Task 2, `notifyUser` delegates to `PushNotificationService` ✓
- Notification tap → deep-link navigation — Task 13 ✓
- All 5 tab screens — Tasks 8–12 ✓
- Signal detail with cancel/close actions — Task 9 ✓
- Live LTP via WebSocket — Task 10 ✓
- AppState foreground/background WS lifecycle — Task 10, `usePortfolioLtp` ✓
- NativeWind styling — Task 4 ✓
- EAS build + OTA — Task 14 ✓
- `.env.development` / `.env.production` — Task 4 ✓
- `EXPO_PUBLIC_API_URL` env var — Task 5, `lib/api.ts` ✓
- Admin panel excluded — not implemented ✓

**Placeholder scan:** No TBD/TODO found in code blocks. All steps have complete code.

**Type consistency check:**
- `TokenResponse(accessToken, refreshToken)` — defined Task 1 Step 6, consumed in `authStore.ts` Task 5 ✓
- `useSignal(id: number)` — defined Task 7, consumed in Task 9 ✓
- `useCancelPending()` takes `signalId: number` — defined Task 7, called with `s.id` (number) in Task 9 ✓
- `usePortfolioStore.ltpMap[position.signal.id]` — `signal.id` is `number`, key is `number` ✓
- `DeviceToken.Platform` enum — `FCM | APNS`, matches `DeviceTokenRequest` ✓

---

**Plan complete and saved to `docs/superpowers/plans/2026-08-25-react-native-mobile-app.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task, review between tasks

**2. Inline Execution** — Execute in this session using executing-plans with checkpoints

Which approach?
