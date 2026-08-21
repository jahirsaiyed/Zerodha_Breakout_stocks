# Task 5 Brief: Auth Module + Security Config

## Context
This is Task 5 of 7 in Phase 1 (Foundation) of a Trading Portfolio Management System for Indian stocks.
Project root: `D:\Zerodha_Breakout_stocks`
Branch: main

## Global Constraints
- Java 21, Spring Boot 3.3.5, package root `com.trading`
- All REST endpoints prefixed `/api`
- JWT in `httpOnly` cookie named `jwt`; 24-hour expiry; `SameSite=Strict`
- All API responses: `{ "success": true|false, "data": ..., "error": null|string }` — use `ApiResponse<T>` from `com.trading.common.ApiResponse`
- Schema owned exclusively by Flyway — `ddl-auto: validate` always
- `ENCRYPTION_KEY` env var: minimum 32 characters; `JWT_SECRET`: minimum 64 characters
- CORS allowed origin from env var `CORS_ALLOWED_ORIGINS` (maps to Spring property `cors.allowed-origin`)

## What already exists (do NOT recreate)
- `com.trading.common.ApiResponse<T>` — record with `success(T data)` and `error(String msg)` factory methods
- `com.trading.common.EncryptionUtil` — AES-256-GCM encryption
- `com.trading.common.GlobalExceptionHandler` — handles BadCredentialsException→401, AccessDeniedException→403, IllegalArgumentException→400, Exception→500
- `com.trading.users.User` — entity with id, name, email, passwordHash, role (UserRole enum), active, createdAt
- `com.trading.users.UserRole` — top-level enum: ADMIN, USER
- `com.trading.users.UserRepository` — has `findByEmail(String)` and `existsByEmail(String)`
- `com.trading.users.UserService` — createUser, getUserByEmail, getAllUsers, setUserActive, getConfigByEmail, updateConfig
- `com.trading.users.dto.UserResponse` — record(Long id, String name, String email, String role, Boolean active)
- `backend/src/main/resources/application.yml` — has `jwt.secret: ${JWT_SECRET}` and `cors.allowed-origin: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}`

## Files to Create

### Production code
- `backend/src/main/java/com/trading/auth/dto/LoginRequest.java`
- `backend/src/main/java/com/trading/auth/JwtUtil.java`
- `backend/src/main/java/com/trading/auth/JwtFilter.java`
- `backend/src/main/java/com/trading/auth/AuthService.java`
- `backend/src/main/java/com/trading/auth/AuthController.java`
- `backend/src/main/java/com/trading/config/SecurityConfig.java`

### Test
- `backend/src/test/java/com/trading/auth/AuthControllerTest.java`

## Exact Implementation

### LoginRequest DTO
```java
package com.trading.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
```

### JwtUtil.java
```java
package com.trading.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private static final long EXPIRY_MS = 24L * 60 * 60 * 1000;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
                .signWith(key)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
```

### JwtFilter.java
```java
package com.trading.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        extractToken(req).ifPresent(token -> {
            try {
                Claims claims = jwtUtil.validateToken(token);
                String role = claims.get("role", String.class);
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) { /* invalid token — Spring Security denies access */ }
        });
        chain.doFilter(req, res);
    }

    private Optional<String> extractToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> "jwt".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
```

### SecurityConfig.java
```java
package com.trading.config;

import com.trading.auth.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;

    @Value("${cors.allowed-origin}") private String allowedOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

### AuthService.java
```java
package com.trading.auth;

import com.trading.auth.dto.LoginRequest;
import com.trading.users.User;
import com.trading.users.UserRepository;
import com.trading.users.dto.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserResponse login(LoginRequest req, HttpServletResponse response) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.getActive()) throw new BadCredentialsException("Account is deactivated");
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash()))
            throw new BadCredentialsException("Invalid credentials");

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true).secure(false).path("/")
                .maxAge(Duration.ofHours(24)).sameSite("Strict").build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), user.getActive());
    }

    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true).secure(false).path("/").maxAge(0).build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
```

### AuthController.java
```java
package com.trading.auth;

import com.trading.auth.dto.LoginRequest;
import com.trading.common.ApiResponse;
import com.trading.users.dto.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(
            @RequestBody @Valid LoginRequest req, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(req, response)));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

### AuthControllerTest.java (TDD — write first, then run RED, then create production classes GREEN)
```java
package com.trading.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.auth.dto.LoginRequest;
import com.trading.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(com.trading.config.SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean JwtUtil jwtUtil;  // JwtFilter depends on this

    @Test
    void login_returns200WithUserOnSuccess() throws Exception {
        UserResponse user = new UserResponse(1L, "Alice", "alice@test.com", "USER", true);
        when(authService.login(any(), any())).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("alice@test.com", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alice@test.com"));
    }

    @Test
    void login_returns401OnBadCredentials() throws Exception {
        when(authService.login(any(), any())).thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("x@x.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

## TDD Workflow
1. Write `AuthControllerTest.java` first
2. Run: `cd backend && ./mvnw test -Dtest=AuthControllerTest` — expect FAIL (classes don't exist)
3. Create production classes in this order: `LoginRequest` → `JwtUtil` → `JwtFilter` → `SecurityConfig` → `AuthService` → `AuthController`
4. Run: `cd backend && ./mvnw test -Dtest=AuthControllerTest` — expect BUILD SUCCESS, 2 tests pass
5. Run all tests: `./mvnw test` — expect all 5 existing + 2 new = 7 tests pass

## Important Notes
- `SecurityConfig` uses `@Value("${cors.allowed-origin}")`. In `application.yml`, this is bound from env var `CORS_ALLOWED_ORIGINS` (already configured). In tests, set `cors.allowed-origin=http://localhost:3000` via `@TestPropertySource` if needed — but the `@WebMvcTest` + `@Import(SecurityConfig.class)` pattern requires either a test application.yml with that property or a `@TestPropertySource`. Add `@TestPropertySource(properties = "cors.allowed-origin=http://localhost:3000")` to `AuthControllerTest` if the test context fails to start due to missing property.
- `UserService` needs a `PasswordEncoder` bean — it's provided by `SecurityConfig`. The existing `UserServiceTest` uses Mockito so doesn't need the Spring context; no circular dependency issue.
- `AuthService.login()` method signature takes `(LoginRequest req, HttpServletResponse response)` — the test mocks `authService.login(any(), any())` which matches both args.
- Do NOT use `.secure(true)` on the cookie — the backend may run over HTTP locally.

## Commit message
```
feat: auth module — JWT, security config, login/logout endpoints
```

## Report
Write your report to: `D:\Zerodha_Breakout_stocks\.superpowers\sdd\task-5-report.md`

Include:
- STATUS: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Files created (list)
- Test results (exact Maven output lines)
- Any deviations from the plan
- Commits made (hash + message)
