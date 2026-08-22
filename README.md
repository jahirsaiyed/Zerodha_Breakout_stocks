# Zerodha Breakout Stocks

A private, self-hosted portfolio management system for Indian equity breakout trading via the Zerodha Kite API.

## What It Does

- **Signal tracking** — Add breakout signals manually or sync from a Google Sheet
- **Automated order placement** — Entry limit orders and GTT OCO (target + stop-loss) orders through Zerodha
- **Live P&L** — Real-time LTP and unrealised P&L via Zerodha market quotes
- **Trade history** — Cumulative P&L chart and closed-trade log
- **Telegram alerts** — Entry fills, target hits, stop-loss triggers, and daily summaries
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

Nginx listens on port 80/443. Configure TLS certificate paths in `nginx/nginx.conf`.

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
| `ZERODHA_API_KEY` | — | Global fallback API key (per-user key takes precedence) |
| `ZERODHA_API_SECRET` | — | Global fallback API secret |
| `TELEGRAM_BOT_TOKEN` | — | Bot token from @BotFather |
| `TELEGRAM_ENABLED` | — | `true` to enable bot polling |
| `GOOGLE_SHEETS_ID` | — | Sheet ID for signal sync |
| `GOOGLE_SHEETS_CREDENTIALS` | — | Path to service account JSON |

See `.env.example` for the complete list with descriptions.

## API Reference

Interactive Swagger UI is available at `http://localhost:9006/swagger-ui.html`.

Key endpoint groups:

| Prefix | Description |
|--------|-------------|
| `POST /api/auth/login` | Authenticate and receive JWT cookie |
| `DELETE /api/auth/logout` | Invalidate session |
| `GET /api/signals` | List / manage trading signals |
| `POST /api/signals/sync` | Trigger Google Sheets sync |
| `GET /api/portfolio/positions` | Positions (all or by status) |
| `GET /api/portfolio/positions/live` | Positions with live LTP |
| `POST /api/portfolio/positions/{id}/exit` | Manual market exit |
| `GET /api/portfolio/orders` | Order history (paginated) |
| `GET/PUT /api/users/me/config` | User configuration |
| `POST /api/users/me/password` | Change password |
| `GET /api/zerodha/login` | Initiate Zerodha OAuth |
| `GET /api/zerodha/callback` | OAuth callback (Zerodha redirects here) |
| `DELETE /api/zerodha/disconnect` | Disconnect Zerodha |
| `GET /api/admin/users` | List all users (ADMIN only) |
| `GET /api/admin/health` | System health (ADMIN only) |

## Running Tests

```bash
# Backend (158 tests)
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
