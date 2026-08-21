# Trading & Portfolio Management System — Design Spec
**Date:** 2026-08-21
**Status:** Approved

---

## Overview

A swing/positional trading and portfolio management system for Indian stocks. The system ingests buy signals (entry, stop loss, target) from a Google Sheet and a web UI, auto-places limit orders on Zerodha, manages exits via GTT OCO orders, and notifies users via Telegram. A React frontend provides portfolio visibility and configuration management.

---

## Scope

- **Signal sources:** Google Sheets (primary) + manual entry via web app
- **Broker:** Zerodha (abstracted for future broker support)
- **Trading style:** Swing / positional (multi-day holds)
- **Users:** Small private group; each user has their own Zerodha account, config, and Telegram chat
- **Frontend:** React web app (React Native mobile planned for future)

---

## 1. Overall Architecture

A **modular monolith** built with Spring Boot 3.x. Internally structured as clean bounded modules that communicate via an in-process Spring `ApplicationEventPublisher`. Single PostgreSQL database. React frontend served as static files behind Nginx.

```
React Frontend
      |
      | REST API (HTTPS / JWT)
      |
Spring Boot — Modular Monolith
  [Signals Module]  [Portfolio Engine]  [Broker Module]  [Notifier Module]
        \                  |                  /                 |
         \_________________|_________________/                  |
                    Domain Events Bus                           |
               (Spring ApplicationEvents)                      |
                                                               |
Scheduler (@Scheduled)                                         |
  - Sheet sync every 15 min (market hours)                     |
  - Core trading loop every 5 min (market hours)               |
  - Order status check every 5 min (market hours)              |
  - Daily jobs (token refresh, reconciliation, summary)        |
      |                                                        |
PostgreSQL DB        Zerodha Kite API          Telegram Bot API
```

**Module boundaries:**
- `signals` — signal lifecycle, sheet sync, manual entry
- `portfolio` — position sizing, order orchestration, position tracking
- `broker` — `BrokerAdapter` interface + `ZerodhaBrokerAdapter` implementation
- `notifications` — Telegram message templates and delivery
- `users` — auth, user config management

---

## 2. Data Model

```sql
users
  id, name, email, password_hash, role (ADMIN|USER), created_at

user_configs
  id, user_id (FK), max_positions, position_sizing_method (EQUAL|FIXED|RISK_BASED),
  position_sizing_value, order_expiry_days, zerodha_api_key, zerodha_api_secret,
  zerodha_access_token (encrypted), telegram_chat_id

signals
  id, symbol, entry_price, stop_loss, target, risk_reward_ratio (computed),
  source (GOOGLE_SHEET|MANUAL), source_ref (sheet row id),
  status (ACTIVE|EXPIRED|CANCELLED), added_at, updated_at, notes

positions
  id, user_id (FK), signal_id (FK), symbol, quantity, avg_entry_price,
  entry_order_id, gtt_order_id,
  status (PENDING_ENTRY|ACTIVE|CANCELLED|CLOSED_TARGET|CLOSED_SL|CLOSED_MANUAL),
  opened_at, closed_at, realised_pnl

orders
  id, user_id (FK), position_id (FK), zerodha_order_id, type (ENTRY|EXIT_TARGET|EXIT_SL),
  order_type (LIMIT|GTT_OCO), symbol, quantity, price,
  status (PENDING|FILLED|CANCELLED|REJECTED), placed_at, updated_at

signal_sync_log
  id, synced_at, source, signals_added, signals_modified, signals_removed, notes
```

**Key rules:**
- `risk_reward_ratio` is computed on insert: `(target - entry) / (entry - stop_loss)`
- Signals are shared across users; positions are per-user
- `zerodha_access_token` stored AES-256 encrypted
- DB unique constraint on `(user_id, signal_id)` for non-closed positions prevents duplicate orders

---

## 3. Signal Module

### Signal Lifecycle

```
Sheet / Manual Entry
        |
    [ACTIVE]
        |
        +-- modified, no active position     --> update DB silently
        +-- removed, no active position      --> status = CANCELLED
        +-- order expiry elapsed, not filled --> status = EXPIRED
        +-- modified, active position exists --> Telegram alert, no auto-action
        +-- all users closed position        --> status = EXPIRED
```

### Google Sheets Sync

- Polls every 15 minutes, Mon–Fri, 9:00 AM – 4:00 PM IST
- Uses Google Sheets API v4 with a service account (admin shares sheet to service account email)
- Each row identified by `source_ref` (row index + symbol)
- Diffs added / modified / removed rows on each poll; logs to `signal_sync_log`

### Signal Scoring Algorithm

When the portfolio engine has open slots, candidates are ranked:

```
score = (0.6 × proximity_score) + (0.4 × risk_reward_score)

proximity_score = 1 - ((current_price - entry_price) / (entry_price - stop_loss))
  - Signals where current_price < stop_loss are disqualified entirely

risk_reward_score = normalised risk_reward_ratio across all candidates
```

Weights (0.6 / 0.4) are configurable in `application.yml`.

### Web App Sync UI

- Sync Now button (triggers immediate out-of-schedule sync)
- Last sync timestamp + added / modified / removed counts
- Manual signal entry form (symbol with NSE validation, entry, SL, target; R:R auto-computed)
- Signals table with status badges; edit allowed for ACTIVE signals without active positions

---

## 4. Portfolio Engine

### Core Loop (every 5 min, 9:15 AM – 3:25 PM IST)

```
For each user:
  1. available_slots = max_positions - count(ACTIVE + PENDING_ENTRY positions)
  2. If available_slots == 0 → skip user
  3. Fetch ACTIVE signals not in user's portfolio
  4. Fetch LTP for all candidates (batched Zerodha Quote API)
  5. Score and rank candidates
  6. For top N = available_slots candidates:
       a. Calculate quantity by position sizing method
       b. Place limit buy order via BrokerAdapter
       c. Create position (status = PENDING_ENTRY)
       d. Publish OrderPlacedEvent
```

### Position Sizing Methods

```
EQUAL:       quantity = floor((available_margin / max_positions) / entry_price)
FIXED:       quantity = floor(position_sizing_value / entry_price)
RISK_BASED:  risk = available_margin × (position_sizing_value / 100)
             quantity = floor(risk / (entry_price - stop_loss))
```

`available_margin` fetched from Zerodha Margins API on each loop iteration.

**Edge case:** If calculated `quantity` floors to 0 (entry price too high relative to allocated capital), the signal is skipped for that user with a warning logged and a Telegram alert: "Skipped SYMBOL — insufficient capital for minimum 1 share at ₹X."

### Order Fill Detection (every 5 min, market hours)

For each PENDING_ENTRY position:
- COMPLETE → mark ACTIVE, place GTT OCO, publish `OrderFilledEvent`
- **Partial fill**: if filled quantity > 0 but < ordered quantity — accept the partial fill, cancel the remaining open order, place GTT OCO for the filled quantity, mark position ACTIVE with `avg_entry_price` and actual `quantity` updated
- CANCELLED / REJECTED → mark position CANCELLED, publish `OrderCancelledEvent`
- Age > `order_expiry_days` → cancel order on Zerodha, mark position CANCELLED, publish `OrderExpiredEvent`

### GTT Exit Reconciliation (3:35 PM IST daily)

Runs before the daily summary so same-day GTT exits are included in the 3:45 PM report.
- Fetch all GTT statuses for each user from Zerodha
- TRIGGERED GTT → compute realised P&L, mark position CLOSED_TARGET or CLOSED_SL, publish `PositionClosedEvent`

### Duplicate Order Prevention

Three layers:
1. DB unique constraint on `(user_id, signal_id)` for non-closed positions
2. Pre-flight check in portfolio engine before every order attempt
3. Zerodha `tag` field set to internal `position_id` — used for reconciliation

### Unmanaged Position Detection (9:10 AM IST daily)

- Fetch all Zerodha holdings per user
- Compare against system's ACTIVE positions
- Holdings not in system → `UnmanagedPositionDetectedEvent` → Telegram alert
- Core loop always skips symbols already in Zerodha holdings (managed or unmanaged)
- Web app allows importing unmanaged positions (link to signal, set GTT) or marking as ignored

### Manual Exit (from web app)

- Cancel GTT on Zerodha
- Place market sell order
- Mark position CLOSED_MANUAL
- Publish `PositionClosedEvent`

---

## 5. Broker Module

### BrokerAdapter Interface

```java
interface BrokerAdapter {
    String placeLimitOrder(String symbol, int quantity, double price, String tag);
    String placeGttOcoOrder(String symbol, int quantity, double stopLoss, double target, String tag);
    void cancelOrder(String orderId);
    void cancelGttOrder(String gttId);
    OrderStatus getOrderStatus(String orderId);
    List<Holding> getHoldings();
    double getAvailableMargin();
    Map<String, Double> getQuotes(List<String> symbols);
    String refreshAccessToken(String apiKey, String apiSecret, String requestToken);
}
```

`ZerodhaBrokerAdapter` is the first implementation. Future brokers implement the same interface.

### Zerodha-Specific Details

**Authentication:**
- Zerodha access tokens expire daily and **cannot be silently refreshed server-side** — a new `request_token` requires the user to complete the Zerodha login page flow each day
- Daily Telegram reminder sent at **8:00 AM IST**: "Please re-login to Zerodha via the web app to activate today's trading session"
- Web app has a **Connect Zerodha** button that redirects to the Zerodha login page; on callback, the system exchanges the `request_token` for a new `access_token` and stores it encrypted
- If a user has not re-authenticated by 9:15 AM IST → trading is skipped for that user with a follow-up Telegram alert
- For users who want fully automated re-login: TOTP-based automation is supported optionally — user provides their Zerodha TOTP secret (stored encrypted); the system completes the login headlessly at 8:30 AM IST using a Playwright/Selenium flow

**Rate Limiting:**
- 10 req/s, 200 req/min per Zerodha limits
- Per-user `RateLimiter` (Guava) wraps all API calls
- LTP fetch batched: up to 500 symbols per request

**GTT OCO Structure:**
```
TWO_LEG GTT:
  Leg 1: trigger at stop_loss → SL-M sell order
  Leg 2: trigger at target    → Limit sell order
```

**Error Handling:**
- `NetworkException` → retry 3x with exponential backoff
- `TokenException` → refresh token, retry once
- `OrderException` → mark order REJECTED, Telegram alert with reason

**NSE Symbol Validation:**
- Instrument list downloaded from Zerodha daily at 8:00 AM IST and cached
- Symbols validated at signal entry time (not at order time)

---

## 6. Notification Module (Telegram)

### Setup

- One shared Telegram bot for the system
- Each user configures their personal `telegram_chat_id`
- Messages sent directly to user's chat — users never see each other's alerts
- Bot commands are read-only; no trading actions via Telegram

### Events & Messages

| Event | Trigger |
|---|---|
| New signal detected | Signal synced from sheet or added manually |
| Order placed | Limit entry order submitted to Zerodha |
| Order filled | Entry order confirmed; GTT set |
| Order expired | Limit order not filled within `order_expiry_days` |
| Target hit | GTT triggered at target price |
| Stop loss hit | GTT triggered at stop loss price |
| Signal changed (active) | Sheet modified while position is open — no auto-action |
| Unmanaged position | Zerodha holding not tracked in system |
| Daily summary | 3:45 PM IST — open positions, P&L, available slots |

### Bot Commands

```
/portfolio  — open positions with current P&L
/signals    — active signals not yet entered
/summary    — today's P&L snapshot
/status     — system health (last sync, scheduler status)
```

### Reliability

- Messages sent asynchronously — failures do not block trading
- Retry up to 3 times with 5-second backoff
- Telegram unavailability logged; system continues trading uninterrupted

---

## 7. React Frontend

### Tech Stack

- React 18, TypeScript, Vite
- TanStack Query (server state + auto-refresh)
- React Router v6
- Shadcn/ui + Tailwind CSS
- Recharts (P&L charts)
- Axios with JWT interceptor

### Pages

**Dashboard**
- Open positions table: symbol, qty, avg entry, LTP, unrealised P&L %, GTT status
- Portfolio P&L summary: today / week / total
- Available slots indicator
- Recent activity feed (last 10 events)

**Signals**
- Active signals table with status badges
- Sync Now button + last sync time + sync log
- Add Signal form with NSE symbol validation and auto-computed R:R
- Edit / cancel for ACTIVE signals without active positions

**Portfolio History**
- Closed positions table: symbol, entry, exit, P&L, duration, exit type
- Daily bar chart + cumulative P&L line chart
- Filter by date range and exit type

**User Settings**
- Max positions, position sizing method + value, order expiry days
- Zerodha API key/secret + Connect Zerodha button
- Telegram chat ID + Send Test Message button

**Admin Panel** (ADMIN role only)
- User management: create, activate/deactivate
- System health: scheduler status, last sync, Zerodha token status per user
- Signal sync log viewer

### Auth

- JWT stored in `httpOnly` cookie (XSS safe)
- All routes protected; admin routes return 403 for non-admins
- Zerodha re-login prompt on dashboard if token expired

---

## 8. Infrastructure

### Deployment

| Component | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Maven |
| Database | PostgreSQL 16 |
| Frontend | React 18, Vite (static files) |
| Reverse proxy | Nginx (static files + `/api` proxy) |
| Server | Single Linux VPS (e.g., DigitalOcean, AWS EC2) |
| Process management | Docker Compose |
| TLS | Let's Encrypt via Certbot |
| Schema migrations | Flyway |
| DB backups | pg_dump daily → local + optional S3 |

### Scheduler Summary

| Job | Time | Purpose |
|---|---|---|
| NSE instrument refresh | 8:00 AM IST daily | Valid symbol cache |
| Zerodha re-login reminder | 8:00 AM IST daily | Telegram prompt to re-authenticate |
| TOTP auto-login (optional) | 8:30 AM IST daily | Headless Zerodha login for TOTP users |
| Holdings reconciliation | 9:10 AM IST daily | Detect unmanaged positions |
| Sheet sync | Every 15 min, 9:00–4:00 PM IST | Pull signals |
| Core trading loop | Every 5 min, 9:15–3:25 PM IST | Place orders |
| Order status check | Every 5 min, 9:15–3:30 PM IST | Detect fills/cancellations |
| GTT reconciliation | 3:35 PM IST daily | Detect GTT-triggered exits (before summary) |
| Daily summary | 3:45 PM IST daily | Telegram summary to all users |

### Security

- HTTPS via Let's Encrypt
- Zerodha API secrets AES-256 encrypted in DB; encryption key in env var
- JWT secret in env var; tokens expire in 24 hours
- CORS restricted to frontend domain
- Role-based access (ADMIN / USER) via Spring Security

### Monitoring

- Spring Boot Actuator `/actuator/health` for uptime checks
- Structured JSON logs (Logback), rotated daily, retained 30 days
- Admin health page in React shows scheduler last-run times and per-user Zerodha token status
