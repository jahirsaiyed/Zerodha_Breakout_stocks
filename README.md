# Zerodha Breakout Stocks

A private, self-hosted portfolio management system for Indian equity breakout trading via the Zerodha Kite API.

## What It Does

- **Signal tracking** — Add breakout signals manually or sync from a Google Sheet
- **Automated order placement** — Entry limit orders and GTT OCO (target + stop-loss) orders through Zerodha
- **Live P&L** — Real-time LTP and unrealised P&L via Zerodha market quotes
- **Trade history** — Cumulative P&L chart and closed-trade log
- **Telegram alerts** — Entry fills, target hits, stop-loss triggers, and daily summaries; each user connects their own bot
- **Margin controls** — Per-user cap on deployable margin: percentage of available margin (1–100 %), an optional fixed ₹ ceiling, or both (system uses the tighter limit)
- **Multi-user** — Supports multiple traders, each with their own Zerodha connection and config
- **Admin panel** — User management and system health dashboard

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3.5 · Java 21 · Spring Security · Spring Data JPA |
| Database | PostgreSQL 16 |
| Frontend | React 19 · TanStack Query v5 · React Router v7 · Tailwind CSS v3 |
| Broker | Zerodha Kite Connect API v3 |
| Notifications | Telegram Bot API |
| Infrastructure | Docker Compose · Nginx · GitHub Actions CI |

## Prerequisites

- Java 21+
- Node.js 20+
- Docker & Docker Compose
- A [Zerodha Kite Connect](https://kite.trade/) developer account

## Quick Start (Development)

### 1. Clone and configure

```bash
git clone https://github.com/jahirsaiyed/Zerodha_Breakout_stocks.git
cd Zerodha_Breakout_stocks
cp .env.example .env
```

Edit `.env` with your values (see **Environment Variables** below).

### 2. Start the database

```bash
docker compose up -d postgres
```

### 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend starts on `http://localhost:9006`.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on `http://localhost:5173`.

### 5. Create the first admin user

```bash
curl -s -X POST http://localhost:9006/api/admin/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Admin","email":"admin@example.com","password":"yourpassword","role":"ADMIN"}'
```

*(First request is unauthenticated only when no users exist; subsequent calls require an ADMIN session cookie.)*

## Production Deployment

```bash
cp .env.example .env        # fill all required values
docker compose -f docker-compose.prod.yml up -d
```

Nginx listens on port 80/443. Configure TLS certificate paths in `nginx/nginx.prod.conf`.

For database backups:

```bash
./scripts/backup.sh         # pg_dump to ./backups/
```

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_URL` | ✓ | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/trading` |
| `DB_USERNAME` | ✓ | Database username |
| `DB_PASSWORD` | ✓ | Database password |
| `JWT_SECRET` | ✓ | ≥ 64-character secret for signing JWTs |
| `ENCRYPTION_KEY` | ✓ | ≥ 32-character AES key for encrypting broker secrets |
| `CORS_ALLOWED_ORIGINS` | ✓ | Frontend URL(s), comma-separated |
| `COOKIE_SECURE` | ✓ | `true` in production (HTTPS), `false` locally |
| `ZERODHA_API_KEY` | — | Kite Connect app API key (shared across all users) |
| `ZERODHA_API_SECRET` | — | Kite Connect app API secret |
| `TELEGRAM_ENABLED` | — | `true` to enable Telegram notifications |
| `SHEETS_SPREADSHEET_ID` | — | Google Sheets spreadsheet ID for signal sync |
| `SHEETS_CREDENTIALS_PATH` | — | Absolute path to service account JSON credentials file |

See `.env.example` for the complete list with descriptions.

> **Note:** Telegram bot tokens are configured per-user via the Settings page — there is no
> system-level shared bot token. `TELEGRAM_ENABLED` controls whether the Telegram feature is
> available at all; each user then connects their own bot through Settings → Your Telegram Bot.

## User Configuration

Each user has a personal config stored in `user_configs`. Key fields:

| Field | Default | Description |
|-------|---------|-------------|
| `maxPositions` | 5 | Maximum simultaneous open positions (1–50) |
| `positionSizingMethod` | `FIXED` | `FIXED`, `EQUAL`, or `RISK_BASED` |
| `positionSizingValue` | 10000 | ₹ per position (FIXED/EQUAL) or risk % per trade (RISK_BASED) |
| `orderExpiryDays` | 5 | Cancel unfilled limit orders after N days |
| `marginUsagePercent` | 100 | Percentage of available Zerodha margin the system may deploy (1–100) |
| `marginUsageFixedLimit` | null | Optional fixed ₹ ceiling. When set, effective margin = min(available × percent/100, fixedLimit) |

Position sizing for `EQUAL` and `RISK_BASED` methods is applied against the **effective margin**
(after both caps). `FIXED` is a flat ₹ amount per trade and ignores margin caps.

## API Reference

Interactive Swagger UI is available at `http://localhost:9006/swagger-ui.html`.

Key endpoint groups:

| Endpoint | Description |
|----------|-------------|
| `POST /api/auth/login` | Authenticate and receive JWT cookie |
| `DELETE /api/auth/logout` | Invalidate session |
| `GET /api/signals` | List / manage trading signals |
| `POST /api/signals/sync` | Trigger Google Sheets sync |
| `GET /api/portfolio/positions` | Positions (all or by status) |
| `GET /api/portfolio/positions/live` | Positions with live LTP |
| `POST /api/portfolio/positions/{id}/cancel` | Cancel a pending entry order |
| `POST /api/portfolio/positions/{id}/exit` | Manual market exit (close active position) |
| `GET /api/portfolio/orders` | Order history (paginated) |
| `GET /api/users/me` | Get current user profile |
| `GET /api/users/me/config` | Get user configuration |
| `PUT /api/users/me/config` | Update user configuration (includes margin caps) |
| `POST /api/users/me/password` | Change password |
| `GET /api/users/me/account-summary` | Available margin, position slot usage, sizing value |
| `POST /api/users/me/telegram/bot` | Connect a personal Telegram bot (validates and stores token) |
| `DELETE /api/users/me/telegram/bot` | Disconnect personal Telegram bot |
| `POST /api/users/me/telegram/test` | Send a test Telegram message |
| `GET /api/users/me/telegram/chats` | List Telegram chats discovered from the user's bot |
| `GET /api/zerodha/login` | Initiate Zerodha OAuth |
| `GET /api/zerodha/callback` | OAuth callback (Zerodha redirects here) |
| `DELETE /api/zerodha/disconnect` | Disconnect Zerodha |
| `GET /api/admin/users` | List all users (ADMIN only) |
| `POST /api/admin/users` | Create a new user (ADMIN only) |
| `GET /api/admin/health` | System health (ADMIN only) |
| `POST /api/admin/portfolio/run-loop` | Manually trigger the portfolio scheduler loop (ADMIN only) |
| `POST /api/admin/portfolio/check-fills` | Manually trigger order fill check (ADMIN only) |
| `POST /api/admin/portfolio/reconcile-gtt` | Reconcile GTT orders against Zerodha (ADMIN only) |

## Setting Up Telegram Notifications

Telegram is configured per-user — each trader connects their own bot:

1. Message **@BotFather** on Telegram and send `/newbot` to create a bot. Copy the token.
2. In the application, go to **Settings → Your Telegram Bot** and paste the token.
3. Start a chat with your bot (or add it to a group/channel), then send it any message.
4. Refresh Settings → **Telegram Notifications** — the chat will appear in the dropdown.
5. Select the chat and click **Save Changes**.

The system uses `TELEGRAM_ENABLED=true` in the environment to activate the feature. Each user's
bot token and chat ID are stored encrypted in the database; no shared bot token is required at the
system level.

## Running Tests

```bash
# Backend (196 tests)
cd backend && ./mvnw test

# Frontend (12 tests)
cd frontend && npm test

# Frontend with coverage
cd frontend && npm run test:coverage
```

## Project Structure

```
Zerodha_Breakout_stocks/
├── backend/                  Spring Boot application
│   └── src/main/java/com/trading/
│       ├── auth/             JWT filter, login rate limiter
│       ├── broker/           Zerodha API client & adapter
│       ├── common/           Encryption, global error handler
│       ├── config/           Security, OpenAPI configuration
│       ├── notifications/    Telegram bot & event listeners
│       ├── portfolio/        Portfolio engine, scheduler, sizing
│       ├── signals/          Signal entities, service, sheet sync
│       ├── users/            User management, admin API
│       └── zerodha/          OAuth flow, TOTP utility
├── frontend/                 React application
│   └── src/
│       ├── components/       Layout, Badge, route guards
│       ├── contexts/         AuthContext
│       ├── lib/              Axios client, TypeScript types
│       ├── pages/            Dashboard, Positions, Signals, etc.
│       └── test/             Vitest tests
├── nginx/                    Production Nginx config
├── scripts/                  Backup script
├── docker-compose.yml        Development compose
├── docker-compose.prod.yml   Production compose
└── .env.example              Environment variable template
```

## License

Private — all rights reserved.
