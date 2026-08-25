STATUS: DONE

COMMITS: ebb7c2e - feat: add device token registration and Firebase push notifications

FILES_CREATED:
- backend/src/main/resources/db/migration/V12__device_tokens.sql
- backend/src/main/java/com/trading/notifications/DeviceToken.java
- backend/src/main/java/com/trading/notifications/DeviceTokenRepository.java
- backend/src/main/java/com/trading/notifications/DeviceTokenRequest.java
- backend/src/main/java/com/trading/notifications/PushNotificationService.java
- backend/src/main/java/com/trading/notifications/DeviceTokenController.java
- backend/src/test/java/com/trading/notifications/PushNotificationServiceTest.java

FILES_MODIFIED:
- backend/pom.xml (added firebase-admin 9.3.0)
- backend/src/main/java/com/trading/notifications/NotificationService.java (wired PushNotificationService)
- backend/src/test/java/com/trading/notifications/NotificationServiceTest.java (added @Mock for PushNotificationService)

TEST_SUMMARY:
- PushNotificationServiceTest: 1/1 PASS
- NotificationServiceTest: 10/10 PASS (pre-existing test, fixed mock injection)
- Full suite: 202 run, 1 failure (DailySchedulerTest - pre-existing), 10 errors (UserControllerTest - pre-existing)
- No regressions introduced

KEY_DESIGN_DECISIONS:
- V12 uses VARCHAR(10) CHECK constraint instead of PostgreSQL ENUM (avoids Flyway complications)
- Push is called in both ifPresentOrElse branches so users without Telegram config still receive push
- Early-return Telegram pattern converted to if/else to ensure push always executes
- PushNotificationService.sendToUser() catches all per-device exceptions and logs warnings only

CONCERNS: none

---

## Fix Report: TOCTOU + @Transactional (task-2 patch)

ISSUE 1 (TOCTOU race — POST /api/users/me/push-token):
- Removed stream-filter existence check (findByUser_Id → anyMatch)
- Replaced with try/catch DataIntegrityViolationException around save()
- If DB UNIQUE constraint fires on concurrent duplicate, exception is caught and 200 is returned (idempotent)
- Import added: org.springframework.dao.DataIntegrityViolationException

ISSUE 2 (Missing @Transactional — DELETE /api/users/me/push-token):
- Added @Transactional on deregister() method
- deleteByUser_IdAndToken is a Spring Data derived-delete requiring a transaction context
- Import added: org.springframework.transaction.annotation.Transactional

FILE CHANGED:
- backend/src/main/java/com/trading/notifications/DeviceTokenController.java

TEST_SUMMARY:
- PushNotificationServiceTest: PASS (no regressions)
- Full suite: same result as pre-patch baseline — 1 pre-existing failure (DailySchedulerTest), 10 pre-existing errors (UserControllerTest context load failure)
- No new failures introduced by this patch
