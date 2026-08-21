# Task 5 Report: Auth Module + Security Config

## STATUS: DONE

## Files Created

### Production Code
- `backend/src/main/java/com/trading/auth/dto/LoginRequest.java`
- `backend/src/main/java/com/trading/auth/JwtUtil.java`
- `backend/src/main/java/com/trading/auth/JwtFilter.java`
- `backend/src/main/java/com/trading/auth/AuthService.java`
- `backend/src/main/java/com/trading/auth/AuthController.java`
- `backend/src/main/java/com/trading/config/SecurityConfig.java`

### Test Code
- `backend/src/test/java/com/trading/auth/AuthControllerTest.java`

## TDD Workflow

1. Wrote `AuthControllerTest.java` first.
2. Ran test — RED confirmed (compilation failure: missing classes).
3. Created production classes in prescribed order: `LoginRequest` → `JwtUtil` → `JwtFilter` → `SecurityConfig` → `AuthService` → `AuthController`.
4. Ran `AuthControllerTest` — GREEN: 2 tests pass.
5. Ran full suite — all 7 tests pass.

## Test Results

### AuthControllerTest (2 tests)
```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.735 s -- in com.trading.auth.AuthControllerTest
```

### Full Suite (7 tests)
```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.853 s -- in com.trading.auth.AuthControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.052 s -- in com.trading.common.EncryptionUtilTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.291 s -- in com.trading.users.UserServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Deviations from Plan

One minor deviation: `@TestPropertySource` was extended to also include `jwt.secret` in addition to `cors.allowed-origin`. The brief mentioned adding `cors.allowed-origin=http://localhost:3000` if context fails. The `JwtUtil` bean in the test context also required `jwt.secret` to initialize (since `SecurityConfig` imports `JwtFilter` which depends on `JwtUtil`). Added:
```
jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha
```
This is test-only, does not affect production configuration.

## Commit

- Hash: `a54fdbf`
- Message: `feat: auth module — JWT, security config, login/logout endpoints`
