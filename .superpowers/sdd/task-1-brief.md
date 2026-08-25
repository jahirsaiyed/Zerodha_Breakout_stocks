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

