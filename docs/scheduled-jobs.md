# Scheduled Jobs

All scheduled jobs run under Spring's `@EnableScheduling` context (configured in `SchedulingConfig`).
Unless noted otherwise, all cron expressions use the **IST (Asia/Kolkata)** timezone and fire only on
weekdays (**Mon–Fri**) to align with NSE market hours (09:15–15:30).

---

## Table of Contents

1. [PortfolioScheduler — Trading Engine Jobs](#1-portfolioscheduler--trading-engine-jobs)
2. [DailyScheduler — Notifications](#2-dailyscheduler--notifications)
3. [SheetSyncScheduler — Google Sheets Sync](#3-sheetsyncscheduler--google-sheets-sync)
4. [InstrumentCacheService — NSE Instrument Cache](#4-instrumentcacheservice--nse-instrument-cache)
5. [TelegramBotService — Bot Command Polling](#5-telegrambotservice--bot-command-polling)
6. [ZerodhaAuthService — OAuth Nonce Cleanup](#6-zerodhaauthorservice--oauth-nonce-cleanup)
7. [LoginRateLimitFilter — Rate Limit Bucket Cleanup](#7-loginratelimitfilter--rate-limit-bucket-cleanup)
8. [Schedule Summary Table](#8-schedule-summary-table)

---

## 1. PortfolioScheduler — Trading Engine Jobs

**File:** `backend/src/main/java/com/trading/portfolio/PortfolioScheduler.java`

This scheduler drives the core trading lifecycle by delegating to `PortfolioEngine`. All four jobs
are active only during market hours on weekdays.

### 1a. Core Loop — Place Entry Orders

| Property | Value |
|---|---|
| Cron | `0 15/15 9-15 * * MON-FRI` |
| Timezone | IST |
| Effective window | 09:15, 09:30, … 15:15 (every 15 min) |
| Method | `PortfolioScheduler.runCoreLoop()` |

**What it does:**

For every user with a valid Zerodha connection, the engine:

1. Counts active positions and calculates available slots (`maxPositions - occupied`).
2. Fetches current Zerodha holdings to build an `occupiedSymbols` set (avoids duplicate orders).
3. Queries `signals` for active candidates not already in the portfolio.
4. Scores and ranks signals via `SignalScoringService`.
5. Sizes each position via `PositionSizingService` (FIXED / EQUAL / RISK_BASED method, respecting margin caps).
6. Places a limit entry order via the Zerodha broker adapter.
7. Records a `PENDING_ENTRY` position in the database.

If the user's access token has expired, that user is skipped with a warning and no error is propagated to other users.

---

### 1b. Order Fill Check

| Property | Value |
|---|---|
| Cron | `0 15/5 9-15 * * MON-FRI` |
| Timezone | IST |
| Effective window | 09:15, 09:20, … 15:10 (every 5 min) |
| Method | `PortfolioScheduler.checkOrderFills()` |

**What it does:**

Polls Zerodha for the status of every `PENDING_ENTRY` position:

- If the entry order has been **filled** → status transitions to `ACTIVE`, GTT exit orders (target + stop-loss) are placed.
- If the entry order has **expired** (end-of-day with no fill) → position is marked `CANCELLED`.

Running every 5 minutes ensures fill detection happens promptly within the trading session.

---

### 1c. GTT Exit Reconciliation

| Property | Value |
|---|---|
| Cron | `0 0/30 9-16 * * MON-FRI` |
| Timezone | IST |
| Effective window | 09:00, 09:30, … 16:00 (every 30 min) |
| Method | `PortfolioScheduler.reconcileGttExits()` |

**What it does:**

Checks all `ACTIVE` positions to see if their GTT (Good-Till-Triggered) exit orders have been
triggered by the market:

- **Target hit** → position closed as `CLOSED_TARGET`, realised P&L recorded.
- **Stop-loss hit** → position closed as `CLOSED_SL`, realised P&L recorded.
- A Telegram notification is sent to the user for each closed position.

Runs slightly beyond market hours (up to 16:00) to catch any late GTT triggers that settle after 15:30.

---

### 1d. Unmanaged Position Detection

| Property | Value |
|---|---|
| Cron | `0 20 9 * * MON-FRI` |
| Timezone | IST |
| Fires at | 09:20 IST — once per trading day |
| Method | `PortfolioScheduler.detectUnmanagedPositions()` |

**What it does:**

Compares Zerodha holdings (fetched from the broker API) against positions tracked in the application
database. Any holding present in Zerodha but **absent from the DB** is flagged as "unmanaged" and a
Telegram alert is sent to the user, prompting manual review.

Runs at 09:20 — five minutes after market open — to give the market time to settle before the scan.

---

## 2. DailyScheduler — Notifications

**File:** `backend/src/main/java/com/trading/portfolio/DailyScheduler.java`

Handles IST-zoned daily Telegram notifications for all users.

### 2a. Zerodha Re-Login Reminder

| Property | Value |
|---|---|
| Cron | `0 0 8 * * MON-FRI` |
| Timezone | IST |
| Fires at | 08:00 IST — before market open |
| Method | `DailyScheduler.sendReloginReminders()` |

**What it does:**

Iterates all `user_configs` rows. For any user whose `zerodha_connected` flag is `false` or whose
`zerodha_access_token` is `null`, a Telegram reminder is sent asking them to reconnect their Zerodha
account before the trading session begins.

This is important because Zerodha access tokens expire daily and must be refreshed each morning
via the OAuth flow.

---

### 2b. Daily P&L Summary

| Property | Value |
|---|---|
| Cron | `0 45 15 * * MON-FRI` |
| Timezone | IST |
| Fires at | 15:45 IST — 15 minutes after market close |
| Method | `DailyScheduler.sendDailySummary()` |

**What it does:**

For each user, builds and sends a Telegram message containing:

- Count of **active** positions.
- Count of **pending entry** positions.
- List of positions **closed today** (since midnight IST) with outcome (Target / SL hit / Manual) and realised P&L.
- Aggregate **today's P&L** (sum of realised P&L for trades closed that day).
- Win/loss count for the day.

If building the summary for a specific user fails, the error is logged as a warning and the loop
continues to the next user.

---

## 3. SheetSyncScheduler — Google Sheets Sync

**File:** `backend/src/main/java/com/trading/signals/SheetSyncScheduler.java`

Keeps trading signals in sync with a configured Google Sheet. Both jobs are no-ops when the
`sheets.enabled` property is `false`.

### 3a. Market-Hours Sync

| Property | Value |
|---|---|
| Cron | `0 0/15 9-15 * * MON-FRI` |
| Timezone | IST |
| Effective window | 09:00, 09:15, … 15:45 (every 15 min) |
| Method | `SheetSyncScheduler.syncDuringMarketHours()` |

**What it does:**

Calls `SheetSyncService.sync()` which reads the Google Sheet and reconciles its signal rows against
the `signals` table in the database:

- **Added** — new rows in the sheet → new `Signal` records inserted.
- **Modified** — changed rows → existing signals updated.
- **Removed** — rows deleted from sheet → signals marked inactive.
- **Skipped** — rows that are malformed or unchanged.

The sync result (added / modified / removed / skipped counts) is logged at INFO level.

---

### 3b. Market-Close Sync

| Property | Value |
|---|---|
| Cron | `0 0 16 * * MON-FRI` |
| Timezone | IST |
| Fires at | 16:00 IST — one final sync after market close |
| Method | `SheetSyncScheduler.syncAtMarketClose()` |

**What it does:**

Same sync logic as 3a. This final run ensures any last-minute edits made to the sheet during the
trading day are captured even if they fell between two 15-minute windows.

---

## 4. InstrumentCacheService — NSE Instrument Cache

**File:** `backend/src/main/java/com/trading/signals/InstrumentCacheService.java`

Maintains an in-memory set of valid NSE equity symbols for signal validation.

### 4a. Startup Load (`@PostConstruct`)

Not a scheduled job, but runs immediately on application start. Downloads the instrument list so
validation is available from the first request.

### 4b. Daily Refresh

| Property | Value |
|---|---|
| Cron | `0 0 8 * * MON-FRI` |
| Timezone | IST |
| Fires at | 08:00 IST — before market open |
| Method | `InstrumentCacheService.refresh()` |

**What it does:**

Downloads the CSV instrument list from Zerodha's public endpoint:

```
https://api.kite.trade/instruments/NSE
```

Parses each row, extracting the `tradingsymbol` (column index 2) for rows where
`instrument_type == "EQ"`. Loads the symbols into a `ConcurrentHashMap`-backed set.

**Fail-open behaviour:** if the download or parse fails (network error, etc.), the existing cache
is preserved and `isValidNseSymbol()` returns `true` for any symbol until the next successful
refresh. This prevents a Zerodha outage from blocking signal creation.

---

## 5. TelegramBotService — Bot Command Polling

**File:** `backend/src/main/java/com/trading/notifications/TelegramBotService.java`

Polls each user's personal Telegram bot for incoming commands.

### Bot Command Poll

| Property | Value |
|---|---|
| Type | `@Scheduled(fixedDelay = 10_000)` |
| Interval | 10 seconds after the previous run completes |
| Method | `TelegramBotService.pollUpdates()` |

**What it does:**

1. Fetches all `user_configs` rows that have a `telegram_bot_token` configured.
2. For each user, calls Telegram's `getUpdates` API using a per-user `lastUpdateId` cursor (so
   no updates are processed twice).
3. Records any newly seen chat metadata (chat ID, title, type) in an in-memory map for the
   UI's chat picker.
4. For each `message` update, checks if the `chat_id` matches the user's configured
   `telegram_chat_id` (security guard — ignores messages from other chats).
5. Routes recognised commands to the appropriate handler:

| Command | Response |
|---|---|
| `/portfolio` | Active + pending positions with qty and avg entry price |
| `/signals` | Currently active signals with entry price and R:R ratio |
| `/summary` | Lifetime closed trade count, win/loss split, total realised P&L |
| `/status` | Bot online status and current UTC timestamp |

`fixedDelay` (not `fixedRate`) is used so overlapping poll runs cannot stack if a poll takes
longer than 10 seconds.

---

## 6. ZerodhaAuthService — OAuth Nonce Cleanup

**File:** `backend/src/main/java/com/trading/zerodha/ZerodhaAuthService.java`

Manages in-memory OAuth state for the Zerodha login flow.

### Expired Nonce Cleanup

| Property | Value |
|---|---|
| Type | `@Scheduled(fixedDelay = 60_000)` |
| Interval | 60 seconds after the previous run completes |
| Method | `ZerodhaAuthService.cleanupExpiredNonces()` |

**What it does:**

OAuth nonces are stored in a `ConcurrentHashMap` with a 10-minute TTL when the user initiates
the Zerodha login flow. This cleanup job sweeps the map every minute and removes any entries
whose `expiresAt` timestamp has passed. Prevents unbounded memory growth in case the OAuth
callback is never completed (e.g. the user closed the browser tab).

---

## 7. LoginRateLimitFilter — Rate Limit Bucket Cleanup

**File:** `backend/src/main/java/com/trading/auth/LoginRateLimitFilter.java`

Enforces a sliding-window rate limit on `POST /api/auth/login`.

### Bucket Cleanup

| Property | Value |
|---|---|
| Type | `@Scheduled(fixedDelay = 60_000)` |
| Interval | 60 seconds after the previous run completes |
| Method | `LoginRateLimitFilter.cleanup()` |

**What it does:**

The rate limiter tracks per-IP attempt counts in a `ConcurrentHashMap<IP, [windowStart, count]>`.
Each entry is valid for a 60-second window. This cleanup job removes entries whose window has
expired, preventing the map from growing indefinitely across long-running sessions.

Maximum allowed attempts: **10 per IP per 60-second window**. Exceeding this returns HTTP 429.

---

## 8. Schedule Summary Table

| Job | Class | Schedule | Active window |
|---|---|---|---|
| Place entry orders | `PortfolioScheduler` | Every 15 min | 09:15–15:15 IST, Mon–Fri |
| Check order fills | `PortfolioScheduler` | Every 5 min | 09:15–15:10 IST, Mon–Fri |
| Reconcile GTT exits | `PortfolioScheduler` | Every 30 min | 09:00–16:00 IST, Mon–Fri |
| Detect unmanaged positions | `PortfolioScheduler` | 09:20 IST | Once per day, Mon–Fri |
| Zerodha re-login reminder | `DailyScheduler` | 08:00 IST | Once per day, Mon–Fri |
| Daily P&L summary | `DailyScheduler` | 15:45 IST | Once per day, Mon–Fri |
| Google Sheet sync (market hours) | `SheetSyncScheduler` | Every 15 min | 09:00–15:45 IST, Mon–Fri |
| Google Sheet sync (close) | `SheetSyncScheduler` | 16:00 IST | Once per day, Mon–Fri |
| NSE instrument cache refresh | `InstrumentCacheService` | 08:00 IST | Once per day, Mon–Fri |
| Telegram bot poll | `TelegramBotService` | Every 10 s (fixedDelay) | Always |
| OAuth nonce cleanup | `ZerodhaAuthService` | Every 60 s (fixedDelay) | Always |
| Login rate limit cleanup | `LoginRateLimitFilter` | Every 60 s (fixedDelay) | Always |

> **Note:** Jobs marked "Always" run 24/7 regardless of market hours or day of week.
> Jobs with a `fixedDelay` schedule measure the delay from the **end** of the previous
> execution, so they will not overlap even if an individual run takes longer than expected.
