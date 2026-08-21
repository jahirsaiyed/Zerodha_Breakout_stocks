STATUS: DONE

FILES_CREATED:
- backend/src/main/java/com/trading/users/User.java
- backend/src/main/java/com/trading/users/UserConfig.java
- backend/src/main/java/com/trading/users/UserRepository.java
- backend/src/main/java/com/trading/users/UserConfigRepository.java
- backend/src/main/java/com/trading/users/UserService.java
- backend/src/main/java/com/trading/users/dto/CreateUserRequest.java
- backend/src/main/java/com/trading/users/dto/UpdateConfigRequest.java
- backend/src/main/java/com/trading/users/dto/UserResponse.java
- backend/src/main/java/com/trading/users/dto/UserConfigResponse.java
- backend/src/test/java/com/trading/users/UserServiceTest.java

TEST_RESULTS:
[INFO] Running com.trading.users.UserServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.124 s -- in com.trading.users.UserServiceTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS

COMMITS:
bedcfb5 - feat: users module — User, UserConfig entities, repositories, UserService

CONCERNS: None. All 3 tests passed on first run after implementing production classes.
TDD workflow followed: test written first (RED — compile failure confirmed), then
production classes created (GREEN — all 3 tests pass).
SecurityConfig (PasswordEncoder bean) not yet created (Task 5), but Mockito mock
handles it correctly in unit tests without a Spring context.
UserConfigResponse deliberately excludes zerodhaApiSecret, zerodhaAccessToken,
and zerodhaTotpSecret per the security constraint. updateConfig() calls
encryptionUtil.encrypt() before persisting zerodhaApiSecret.

## Fix Report
STATUS: FIXED
CHANGES:
- backend/src/main/java/com/trading/users/User.java — replaced LocalDateTime field initializer with @CreationTimestamp; removed inner UserRole enum
- backend/src/main/java/com/trading/users/UserConfig.java — replaced LocalDateTime field initializer with @UpdateTimestamp; removed inner PositionSizingMethod enum
- backend/src/main/java/com/trading/users/UserRole.java — new top-level enum file (ADMIN, USER)
- backend/src/main/java/com/trading/users/PositionSizingMethod.java — new top-level enum file (EQUAL, FIXED, RISK_BASED)
- backend/src/main/java/com/trading/users/UserService.java — changed User.UserRole.USER reference to UserRole.USER; removed cfg.setUpdatedAt(LocalDateTime.now()); removed unused LocalDateTime import
- backend/src/main/java/com/trading/users/dto/CreateUserRequest.java — changed import from com.trading.users.User to com.trading.users.UserRole; field type User.UserRole changed to UserRole
- backend/src/main/java/com/trading/users/dto/UpdateConfigRequest.java — changed import from com.trading.users.UserConfig to com.trading.users.PositionSizingMethod; field type UserConfig.PositionSizingMethod changed to PositionSizingMethod
- backend/src/test/java/com/trading/users/UserServiceTest.java — changed User.UserRole.USER reference to UserRole.USER
TEST_RESULTS:
[INFO] Running com.trading.users.UserServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.196 s -- in com.trading.users.UserServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
COMMIT: 7543bd0 - fix: promote enums to top-level, use @CreationTimestamp/@UpdateTimestamp on entities
