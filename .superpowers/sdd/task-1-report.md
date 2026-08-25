# Task 1 Implementation Report — Backend Token Auth

## Status: DONE_WITH_CONCERNS

## Commit
`0d96590` — feat: add mobile token auth endpoints and refresh token rotation

## What Was Implemented

### New Files
- `backend/src/main/resources/db/migration/V11__refresh_tokens.sql` — refresh_tokens table with BIGSERIAL PK, user_id FK (CASCADE DELETE), token_hash VARCHAR(64) UNIQUE, expires_at, revoked, created_at + user index
- `backend/src/main/java/com/trading/auth/RefreshToken.java` — JPA entity, Lombok @Data/@Builder/@NoArgsConstructor/@AllArgsConstructor, @ManyToOne(LAZY) to User
- `backend/src/main/java/com/trading/auth/RefreshTokenRepository.java` — findByTokenHash + @Modifying deleteExpiredAndRevoked JPQL
- `backend/src/main/java/com/trading/auth/dto/TokenResponse.java` — record(String accessToken, String refreshToken)
- `backend/src/main/java/com/trading/auth/dto/RefreshRequest.java` — record(@NotBlank String refreshToken)
- `backend/src/main/java/com/trading/auth/MobileAuthService.java` — login/refresh(rotation)/revoke; SHA-256 token hashing; 30-day refresh TTL
- `backend/src/main/java/com/trading/auth/MobileAuthController.java` — POST /api/auth/token|refresh|revoke
- `backend/src/test/java/com/trading/auth/MobileAuthControllerTest.java` — 4 WebMvcTest cases

### Modified Files
- `JwtUtil.java` — added `generateAccessToken()` (15-min expiry); existing `generateToken()` (24h, web cookie) unchanged
- `JwtFilter.java` — `extractToken()` now tries `Authorization: Bearer` header first, falls back to `jwt` cookie
- `SecurityConfig.java` — added `/api/auth/token`, `/api/auth/refresh`, `/api/auth/revoke` to permit-all list

## Test Summary

### MobileAuthControllerTest: 4/4 PASS
- `token_returnsAccessAndRefreshTokenOnValidCredentials` — PASS
- `token_returns401OnBadCredentials` — PASS
- `refresh_returnsNewTokenPairOnValidToken` — PASS
- `revoke_returns200AndDelegates` — PASS

### Full Suite: 201 tests run, 1 failure, 10 errors — all pre-existing

## Concerns

Two test classes had pre-existing failures (confirmed by stashing Task 1 changes and re-running against baseline commit `2dcc113`):

1. **UserControllerTest** (10 errors, ApplicationContext failure) — `UserController` constructor gained a `ZerodhaAuthService` parameter in a prior commit, but `UserControllerTest` was not updated to mock it. Root cause: `Error creating bean 'userController': Unsatisfied dependency — No qualifying bean of type 'com.trading.zerodha.ZerodhaAuthService'`. Fix: add `@MockBean ZerodhaAuthService zerodhaAuthService` to UserControllerTest.

2. **DailySchedulerTest** (1 failure) — pre-existing assertion mismatch unrelated to this task.

Neither failure is caused by or related to the Task 1 changes. The new `MobileAuthControllerTest` passes cleanly. No regressions introduced.
