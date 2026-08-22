# Phase 3: Zerodha Integration & Production Infrastructure

**Date:** 2026-08-22
**Branch:** phase-3-zerodha-infra
**Base:** main (HEAD: a97884c)

## Tasks

- [x] Task 1: Zerodha OAuth flow (Connect button → callback → token exchange)
- [x] Task 2: NSE instrument cache + symbol validation
- [x] Task 3: Daily scheduler jobs (re-login reminder + daily P&L summary)
- [x] Task 4: Telegram bot commands (/portfolio /signals /summary /status)
- [x] Task 5: Production infrastructure (Docker, Nginx, CI/CD, .env.example)
- [x] Task 6: Frontend — Zerodha connect flow + callback page

## Task 1: Zerodha OAuth Flow

### New files
- `com/trading/zerodha/ZerodhaAuthService.java` — initiate/complete OAuth, disconnect
- `com/trading/zerodha/ZerodhaAuthController.java` — GET /api/zerodha/login, /callback, /status, DELETE /disconnect
- `com/trading/zerodha/TotpUtil.java` — RFC 6238 TOTP code generation

### Modified files
- `ZerodhaProperties.java` — add loginBaseUrl, frontendUrl
- `BrokerAdapterFactory.java` — add exchangeToken() for unauthenticated token exchange
- `SecurityConfig.java` — permit /api/zerodha/callback
- `application.yml` — add zerodha.login-base-url, zerodha.frontend-url

### OAuth flow
1. `GET /api/zerodha/login` (authenticated) — generate nonce, set short-lived cookie, redirect to Zerodha
2. `GET /api/zerodha/callback?request_token=xxx&status=success` — read nonce cookie, exchange token, store encrypted, redirect to frontend
3. `GET /api/zerodha/status` — returns { connected: bool, connectedAt? }
4. `DELETE /api/zerodha/disconnect` — clears access token, sets zerodhaConnected=false

### TOTP
- User may optionally store their Zerodha TOTP secret (encrypted)
- `GET /api/zerodha/totp` — generate and return current TOTP code (for copy-paste during manual login)
- RFC 6238 implemented without external library

## Task 2: NSE Instrument Cache + Symbol Validation

### New files
- `com/trading/signals/InstrumentCacheService.java` — daily download + in-memory cache

### Modified files
- `SignalService.java` — validate symbol against cache before saving
- `PositionRepository.java` — add findByUserIdAndStatusInAndClosedAtAfter

### Behaviour
- Downloads https://api.kite.trade/instruments/NSE (public CSV) at startup and 8:00 AM IST
- If download fails: cache kept (or empty if first run) — validation skipped gracefully
- isValidNseSymbol() returns true if cache is empty (fail-open to avoid blocking manual entry)

## Task 3: Daily Scheduler Jobs

### New files
- `com/trading/portfolio/DailyScheduler.java`

### Jobs
- `0 0 8 * * MON-FRI Asia/Kolkata` — re-login reminder: notify users with zerodhaConnected=false or null access token
- `0 45 15 * * MON-FRI Asia/Kolkata` — daily P&L summary per user: active positions, pending entries, trades closed today with realised P&L

## Task 4: Telegram Bot Commands

### New files
- `com/trading/notifications/TelegramBotService.java` — @Scheduled polling every 10s

### Commands
- /portfolio — open positions (symbol, qty, status)
- /signals — active signals count + list
- /summary — today's closed trades + P&L
- /status — last sheet sync time, scheduler health
- Unknown command — "Unknown command. Try /portfolio /signals /summary /status"

### User lookup
- `from.id` in Telegram update → lookup UserConfig by telegramChatId
- Commands from unrecognised chat IDs are silently ignored

## Task 5: Production Infrastructure

### Bug fix
- docker-compose.yml port 8080 → 9006
- backend/Dockerfile EXPOSE 8080 → 9006

### New files
- `docker-compose.prod.yml` — production compose: backend+postgres+nginx, no exposed ports except 80/443
- `nginx/nginx.conf` — TLS termination (cert paths), /api proxy to backend, static frontend
- `.github/workflows/ci.yml` — build + test on push/PR to main
- `.env.example` — documented all environment variables
- `scripts/backup.sh` — pg_dump to local + optional S3 upload

## Task 6: Frontend

### New files
- `frontend/src/pages/ZerodhaCallbackPage.tsx` — shown after OAuth redirect, displays success/error

### Modified files
- `frontend/src/App.tsx` — add /zerodha/callback route
- `frontend/src/pages/SettingsPage.tsx` — Connect/Disconnect Zerodha button, zerodha status badge, TOTP display
- `frontend/src/lib/types.ts` — UserConfig: add zerodhaConnected field (already mapped in backend)
