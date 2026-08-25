# React Native Mobile App — Design Spec

**Date:** 2026-08-25
**Branch:** feat/signals-ltp-rank
**Status:** Approved

---

## Overview

A React Native companion app (Expo Managed Workflow) for the existing Zerodha Breakout Stocks trading system. The app connects to the same Spring Boot backend, provides full trader-facing feature parity with the web frontend, and adds native push notifications on top of the existing Telegram alerts.

**Not in scope:** Admin panel (user management, system health) — admin features remain web-only.

---

## Architecture

### Monorepo Placement

```
Zerodha_Breakout_stocks/
├── backend/          # Spring Boot — extended with token auth + push
├── frontend/         # React web app — unchanged
├── mobile/           # NEW — Expo managed React Native app
│   ├── app/          # Expo Router file-based navigation
│   ├── components/   # Shared UI components
│   ├── hooks/        # Data hooks (TanStack Query)
│   ├── lib/          # API client, types, constants
│   └── store/        # Zustand for local state
└── docker-compose.yml
```

### Technology Choices

| Concern | Library | Reason |
|---|---|---|
| Framework | Expo Managed Workflow | Fast bootstrap, OTA updates, all required native APIs available |
| Navigation | Expo Router | File-based routing, mirrors web patterns |
| Data fetching | TanStack Query v5 | Same as web frontend — reuse patterns |
| Secure storage | `expo-secure-store` | Store JWT tokens encrypted on device |
| Push notifications | `expo-notifications` | FCM (Android) + APNs (iOS) unified API |
| OAuth browser | `expo-web-browser` | Zerodha OAuth in-app browser |
| Local state | Zustand | Lightweight, same pattern as web |
| Styling | NativeWind | Tailwind for React Native — consistent design language |
| Live LTP | Native WebSocket API | Connects to existing backend WS endpoint |

---

## Navigation & Screens

### Route Structure

```
app/
├── (auth)/
│   ├── login.tsx             # Email/password login
│   └── zerodha-connect.tsx   # Zerodha OAuth via in-app browser
├── (tabs)/
│   ├── _layout.tsx           # Bottom tab bar
│   ├── dashboard.tsx         # Live P&L summary + margin overview
│   ├── signals.tsx           # Signal list (active / pending / closed)
│   ├── portfolio.tsx         # Active positions with live LTP
│   ├── history.tsx           # Closed trades + cumulative P&L chart
│   └── settings.tsx          # Config, Telegram bot, margin caps, pause trading
├── signals/
│   └── [id].tsx              # Signal detail + cancel/close actions
└── _layout.tsx               # Root layout (auth gate)
```

### Screen Responsibilities

| Screen | Key Features |
|---|---|
| Dashboard | Account margin, unrealised P&L, open positions count, quick stats |
| Signals | Filter by status, sync from Google Sheet, add manual signal |
| Signal Detail | View details, edit, cancel pending order, close active position |
| Portfolio | Live LTP via WebSocket, unrealised P&L per position |
| History | Closed trades table, cumulative P&L line chart |
| Settings | Zerodha reconnect, Telegram bot setup, margin % / ₹ cap, pause trading/sync |

---

## Authentication

### Token-Based JWT Flow (new — mobile only)

The existing web app continues using cookie-based auth unchanged. The mobile app uses a parallel Bearer token path.

```
POST /api/auth/token
  body: { email, password }
  response: { accessToken, refreshToken }
         ↓
expo-secure-store saves both tokens
         ↓
Axios interceptor attaches: Authorization: Bearer <accessToken>
         ↓
On 401 → auto-refresh via POST /api/auth/refresh
         ↓
On refresh failure → clear tokens → redirect to (auth)/login
```

- **Access token:** 15-minute lifetime
- **Refresh token:** 30-day lifetime, stored securely, single-use rotation

### Zerodha OAuth on Mobile

1. User taps "Connect Zerodha" in settings or post-login prompt
2. `expo-web-browser` opens Kite login page
3. After user authenticates, Kite redirects to `GET /api/zerodha/callback?request_token=...`
4. Backend detects mobile `User-Agent` and redirects to deep link: `zbs://zerodha-callback?request_token=...`
5. App receives deep link, completes token exchange silently

Deep-link scheme: `zbs://`

### App Startup Auth Gate

```
App opens
  → Read expo-secure-store for accessToken
  → Valid token? → navigate to (tabs)/dashboard
  → Missing/expired?
      → Attempt refresh
      → Success? → navigate to (tabs)/dashboard
      → Failure?  → navigate to (auth)/login
```

---

## Data Layer

### API Client (`lib/api.ts`)

Axios instance with:
- `baseURL` from `EXPO_PUBLIC_API_URL` env variable
- Request interceptor: attach `Authorization: Bearer <token>`
- Response interceptor: on 401, attempt refresh → retry → logout

### Query Hooks (`hooks/queries/`)

Mirror the web frontend's query structure:

| Hook | Endpoint | Notes |
|---|---|---|
| `useAccountSummary` | `GET /api/users/me/account-summary` | Margin, sizing value |
| `useSignals` | `GET /api/signals` | Filterable by status |
| `useSignal(id)` | `GET /api/signals/:id` | Single signal detail |
| `usePortfolio` | `GET /api/portfolio` | Active positions |
| `useHistory` | `GET /api/portfolio/history` | Closed trades |
| `useUserConfig` | `GET /api/users/me/config` | Margin caps, pause flags |

### Mutation Hooks (`hooks/mutations/`)

| Hook | Endpoint | Notes |
|---|---|---|
| `useSyncSignals` | `POST /api/signals/sync` | Google Sheet sync |
| `useAddSignal` | `POST /api/signals` | Manual signal entry |
| `useCancelPending` | `POST /api/signals/:id/cancel` | Cancel pending order |
| `useClosePosition` | `POST /api/signals/:id/close` | Market close |
| `useUpdateConfig` | `PUT /api/users/me/config` | Save margin settings |
| `useConnectTelegram` | `POST /api/users/me/telegram/bot` | Link Telegram bot |

### Live LTP — WebSocket

- `usePortfolioLtp` hook manages a single WebSocket connection
- Connects when app foregrounds (`AppState` listener)
- Disconnects when app backgrounds (saves battery)
- Merges live LTP into portfolio positions held in Zustand

---

## Push Notifications

### Registration Flow

1. On first login, call `expo-notifications.requestPermissionsAsync()`
2. Get Expo push token → exchange for FCM/APNs native token
3. `POST /api/users/me/push-token` with `{ token, platform: 'FCM' | 'APNS' }`
4. Backend stores in `device_tokens` table

### Notification Events

All events already fired to Telegram are also sent as push notifications:

| Event | Deep Link |
|---|---|
| Entry order filled | `zbs://signals/:id` |
| Target hit | `zbs://signals/:id` |
| Stop-loss triggered | `zbs://signals/:id` |
| Daily P&L summary | `zbs://history` |

### Tap Behaviour

Tapping a notification navigates to the relevant screen via Expo Router's deep-link handling.

---

## Backend Changes

### New Endpoints

| Endpoint | Description |
|---|---|
| `POST /api/auth/token` | Issue `{ accessToken, refreshToken }` in response body |
| `POST /api/auth/refresh` | Accept refresh token, return new token pair |
| `POST /api/auth/logout` | Revoke refresh token |
| `POST /api/users/me/push-token` | Register FCM/APNs device token |
| `DELETE /api/users/me/push-token` | Deregister on logout |

### New DB Migrations

| Migration | Schema |
|---|---|
| V9 | `refresh_tokens (id, user_id, token_hash, expires_at, revoked, created_at)` |
| V10 | `device_tokens (id, user_id, token, platform, created_at)` |

### Spring Security Changes

- Add `BearerTokenAuthenticationFilter` alongside existing `CookieAuthenticationFilter` — web app unaffected
- `JwtService` gains `generateRefreshToken()`, `validateRefreshToken()`, `revokeRefreshToken()`
- New `PushNotificationService` wraps Firebase Admin SDK (FCM) and APNs HTTP/2 API
- Existing `NotificationService` (Telegram) delegates to `PushNotificationService` in parallel

### Zerodha Callback — Mobile Support

- `GET /api/zerodha/callback` detects mobile requests via `User-Agent` header
- On mobile: redirect to `zbs://zerodha-callback?request_token=...` instead of web redirect

### What Does NOT Change

- Cookie-based auth for the web app
- All existing API endpoints and their contracts
- Frontend code

---

## Environment Config

```
mobile/
├── .env.development     # EXPO_PUBLIC_API_URL=http://localhost:9006
├── .env.production      # EXPO_PUBLIC_API_URL=https://your-domain.com
└── app.config.ts        # Deep-link scheme: "zbs://", reads env vars
```

---

## Build & Distribution

### EAS (Expo Application Services)

- `eas build --platform all` — cloud builds for Android AAB and iOS IPA
- `eas update` — OTA JS bundle updates for patch releases without app store submission

### CI (GitHub Actions)

Extend existing workflow:
- On `main` merge: run `eas build --platform all --non-interactive`
- On hotfix tags: run `eas update --branch production`

### Pre-Launch Checklist

- [ ] Register app on Google Play Console (Android)
- [ ] Register app on Apple Developer Program (iOS)
- [ ] Configure Firebase project, add `google-services.json` (Android) and `GoogleService-Info.plist` (iOS)
- [ ] Add APNs key in Apple Developer Portal
- [ ] Add `FCM_SERVER_KEY` and APNs credentials to backend `.env`
- [ ] Set deep-link scheme `zbs://` in `app.config.ts` and register on both stores

---

## Testing Strategy

| Type | Tool | Scope |
|---|---|---|
| Unit | Jest + React Native Testing Library | Hooks, API client, utility functions |
| Component | RNTL | Signals list, portfolio, settings screens |
| E2E | Detox | Login, Zerodha connect, cancel/close flows |

Target: 80% coverage on business-logic hooks and API client.

---

## Out of Scope

- Admin panel (user management, system health) — web-only
- Charting beyond the existing cumulative P&L line chart
- Biometric authentication (can be added post-launch via `expo-local-authentication`)
- Offline mode
