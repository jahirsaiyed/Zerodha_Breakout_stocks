# React Native Mobile App — SDD Progress Ledger

Plan: docs/superpowers/plans/2026-08-25-react-native-mobile-app.md
Branch: feat/signals-ltp-rank
Base commit (before tasks): 2dcc113

## Tasks

- [x] Task 1: Backend token auth (V11, refresh tokens, mobile endpoints, JWT filter)
- [x] Task 2: Backend device tokens + push notifications (V12, Firebase)
- [x] Task 3: Backend Zerodha mobile callback
- [x] Task 4: Expo project scaffold
- [x] Task 5: API client + types + auth store
- [x] Task 6: Auth screens + root layout
- [x] Task 7: Query hooks + mutation hooks
- [x] Task 8: Tab layout + dashboard screen
- [x] Task 9: Signals screen + signal detail
- [x] Task 10: Portfolio screen + WebSocket LTP hook
- [x] Task 11: History screen
- [x] Task 12: Settings screen
- [x] Task 13: Push notification registration + deep-link handling
- [x] Task 14: GitHub Actions EAS build workflow

## Completed

Task 1: complete (commits 2dcc113..0d96590, review APPROVED)
Task 2: complete (commits 0d96590..82703ce, review APPROVED after fix — TOCTOU race fixed, @Transactional added to deregister)
Task 3: complete (commits 82703ce..8579502, review APPROVED)
Task 4: complete (commits 8579502..a382928, review APPROVED — tailwindcss v3 correct for NativeWind 4.x)
  Minor: sha256() could be private; test missing $.data null assertion — not blocking
  Pre-existing: UserControllerTest (missing @MockBean ZerodhaAuthService), DailySchedulerTest failure — unrelated to Task 1
Task 5: complete (commits a382928..b739c81, review APPROVED after fix — pending request queue properly drained on refresh failure)
Task 6: complete (commits b739c81..138173f, review APPROVED)
  Minor: unused `api` import in zerodha-connect; unused `isAuthenticated` in root layout — not blocking
Task 7: complete (commits 138173f..75fea9e, review APPROVED)
  Minor: renderHook test approach deviated due to React 19 / RNTL v14 incompatibility; queryFn tested directly instead — hook implementations unchanged
Task 8: complete (commits 75fea9e..2cade20, review APPROVED after fix — logout button added to dashboard header)
Task 9: complete (commits 2cade20..4f854ed, review APPROVED)
Task 10: complete (commits 4f854ed..8fe1211, review APPROVED)
  Minor: WS error silent; token expiry not re-handled on WS reconnect; no error state UI — all acceptable for MVP
Task 11: complete (commits 8fe1211..b264037, review APPROVED)
Task 12: complete (commits b264037..76d96d5, review APPROVED)
Task 13: complete (commits 76d96d5..6e3a859, review APPROVED)
  Verified: backend /api/users/me/push-token exists (DeviceTokenController @PostMapping("/push-token"))
Task 14: complete (commits 6e3a859..5005796, review APPROVED after fix — expo-github-action@v8 used, distribution:store added)

Final whole-branch review: APPROVED after fix (commit 40b0bc5)
  Fixed: FirebaseConfig.java conditional init, PushNotificationService guard, auth gate loading state,
         usePushNotifications moved to tabs layout (post-auth), Linking sub cleanup, logout deregisters device token
  Accepted deviations: Expo SDK 57 (plan said 51 — Task 4 reviewer approved scaffold as-is), Zustand v5 (plan said v4 — API-compatible)
  Accepted risks: JWT in WS URL for LTP (15-min token, low risk); no WS reconnect on drop (AppState covers foreground)
  Known gap: refresh_tokens table has no scheduled cleanup (deleteExpiredAndRevoked exists but no @Scheduled caller)

---

# Phase 1 Foundation — SDD Progress Ledger

Plan: docs/superpowers/plans/2026-08-21-phase-1-foundation.md
Branch: main
Base commit (before tasks): bfc995c

## Tasks

- [x] Task 1: Project Scaffold + Docker Compose
- [x] Task 2: Database Schema + Spring Boot Bootstrap
- [x] Task 3: Common Module
- [x] Task 4: Users Module
- [x] Task 5: Auth Module + Security Config
- [x] Task 6: User Config + Admin API
- [x] Task 7: React Frontend Scaffold + Login

## Completed

Task 1: complete (commits bfc995c..efc1bd4, review APPROVED)
  Minor note for Task 5: .env.example uses CORS_ALLOWED_ORIGINS — backend SecurityConfig must match this name
Task 2: complete (commit 05118fa, review APPROVED)
Task 3: complete (commit 9134f85, review APPROVED)
Task 4: complete (commits bedcfb5..3b208d6, review APPROVED with concerns resolved)
Task 5: complete (commits a54fdbf..ffbd146, review APPROVED with concerns resolved)
Task 6: complete (commit 40bf36f, review APPROVED — mechanical task, spec match confirmed)
Task 7: complete (commits bbb8ea2..fe4f837, review APPROVED with concerns resolved)
  Note: npm resolved React 19/Router v7/TS 6 (newer than plan spec React 18/v6/TS5); APIs used are backward-compatible; build passes

Final whole-branch review: CRITICAL issues fixed (commit ad8274e)
  Fixed: ENCRYPTION_KEY/JWT_SECRET startup length validation, CORS multi-origin split, logout SameSite=Strict
  Known limitations (accepted for Phase 1):
  - Deactivated users retain JWT for up to 24h (stateless JWT tradeoff; token revocation deferred)
  - zerodhaApiKey returned in config response (Zerodha API keys are semi-public in login URLs)
  - getAllUsers() has no pagination (acceptable for small private user base)
  - CSRF disabled, mitigated by SameSite=Strict + production COOKIE_SECURE=true

Phase 1 Foundation: ALL 7 TASKS COMPLETE (verified 2026-08-22)
Base: bfc995c → HEAD: ad8274e
  Fixes: cookie.secure property-driven via COOKIE_SECURE env var, CORS covers /actuator/**, added logout+@Valid tests
  Fixes: @Builder.Default on UserConfig+User defaults, @CreationTimestamp/@UpdateTimestamp, top-level enums, readOnly transactions
  Known gap: zerodhaAccessToken/zerodhaTotpSecret have no update path yet — deferred to Zerodha integration task

---

# Phase 2 Signals Module — SDD Progress Ledger

Plan: docs/superpowers/plans/2026-08-22-phase-2-signals-module.md

## Tasks

- [x] Task 1: Signal JPA entities + repositories
- [x] Task 2: Signals service + REST API
- [x] Task 3: Tests (SignalServiceTest + SignalControllerTest)

## Completed

Task 1: complete — Signal, Position, Order, SignalSyncLog entities; all enums (SignalSource, SignalStatus,
  PositionStatus, OrderType, OrderKind, OrderStatus); SignalRepository, PositionRepository, OrderRepository,
  SignalSyncLogRepository
Task 2: complete — SignalService (list/create/update/cancel + R:R validation), SignalController
  (GET/POST/PUT/DELETE /signals, GET /signals/sync-log), DTOs (SignalResponse, CreateSignalRequest,
  UpdateSignalRequest, SyncLogResponse)
Task 3: complete — SignalServiceTest (unit), SignalControllerTest (integration)

Phase 2 Signals Module: ALL 3 TASKS COMPLETE (verified 2026-08-22)

---

# Phase 3 Zerodha Integration & Production Infrastructure — SDD Progress Ledger

Plan: docs/superpowers/plans/2026-08-22-phase-3-zerodha-infra.md

## Tasks

- [x] Task 1: Zerodha OAuth flow (Connect button → callback → token exchange)
- [x] Task 2: NSE instrument cache + symbol validation
- [x] Task 3: Daily scheduler jobs (re-login reminder + daily P&L summary)
- [x] Task 4: Telegram bot commands (/portfolio /signals /summary /status)
- [x] Task 5: Production infrastructure (Docker, Nginx, CI/CD, .env.example)
- [x] Task 6: Frontend — Zerodha connect flow + callback page

## Completed

Task 1: complete — ZerodhaAuthService, ZerodhaAuthController (GET /api/zerodha/login, /callback, /status,
  DELETE /disconnect, GET /totp), TotpUtil (RFC 6238); SecurityConfig permits /api/zerodha/callback
Task 2: complete — InstrumentCacheService (daily NSE CSV download + in-memory cache); SignalService
  validates symbol against cache (fail-open when cache empty)
Task 3: complete — DailyScheduler (8:00 AM re-login reminder, 3:45 PM P&L summary per user)
Task 4: complete — TelegramBotService (@Scheduled polling, /portfolio /signals /summary /status commands,
  lookup by telegramChatId, unknown command handling)
Task 5: complete — docker-compose.prod.yml, nginx/nginx.prod.conf (TLS termination + /api proxy),
  .github/workflows/ci.yml (build + test on push/PR), scripts/backup.sh (pg_dump + optional S3)
Task 6: complete — ZerodhaCallbackPage.tsx, /zerodha/callback route in App.tsx,
  SettingsPage.tsx (Connect/Disconnect button, status badge, TOTP display)

Phase 3 Zerodha Infra: ALL 6 TASKS COMPLETE (verified 2026-08-22)
