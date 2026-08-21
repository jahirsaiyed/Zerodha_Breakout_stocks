# Architecture Decision Records
**Project:** Trading & Portfolio Management System
**Date:** 2026-08-21

---

## ADR-001: Modular Monolith over Microservices

**Status:** Accepted

**Context:**
The system has multiple distinct concerns: signal ingestion, portfolio management, broker integration, and notifications. We needed to decide how to structure these.

**Decision:**
Use a single Spring Boot application internally structured as bounded modules (`signals`, `portfolio`, `broker`, `notifications`, `users`), communicating via Spring `ApplicationEventPublisher`.

**Alternatives considered:**
- Microservices (separate deployable services + message queue)
- True monolith (no internal boundaries)

**Rationale:**
The user base is a small private group. Microservices add significant operational overhead (service discovery, inter-service networking, distributed tracing) that is not justified at this scale. A modular monolith provides clean internal boundaries that enforce separation of concerns, while remaining simple to deploy and debug. Modules can be extracted into services later if scale demands it.

---

## ADR-002: BrokerAdapter Interface for Broker Abstraction

**Status:** Accepted

**Context:**
The system currently targets Zerodha only, but additional brokers (Upstox, Angel One, etc.) are planned for the future.

**Decision:**
Define a `BrokerAdapter` Java interface with all broker operations (place order, cancel order, get holdings, get quotes, etc.). `ZerodhaBrokerAdapter` is the first concrete implementation. New brokers implement the same interface without changes to the Portfolio Engine or Signal Module.

**Rationale:**
The Open/Closed Principle — open for extension (new broker implementations), closed for modification (existing trading logic unchanged). This keeps broker-specific concerns (Zerodha API quirks, rate limits, auth) fully isolated.

---

## ADR-003: SignalSource Interface for Signal Abstraction

**Status:** Accepted

**Context:**
Google Sheets is the initial signal source, but other sources (custom screeners, paid signal services, algorithmic strategies) may be added in future.

**Decision:**
Define a `SignalSource` interface. `GoogleSheetsSignalSource` and `ManualSignalSource` are the first two implementations. The Signal Module works against the interface, not the concrete class.

**Rationale:**
Same reasoning as ADR-002. Signal ingestion logic is independent of where signals come from.

---

## ADR-004: Zerodha Daily Re-Authentication Strategy

**Status:** Accepted

**Context:**
Zerodha access tokens expire daily. Unlike OAuth2 refresh tokens, Zerodha's Kite API requires a new `request_token` obtained through a browser-based login every day. There is no silent server-side refresh.

**Decision:**
Two-tier approach:
1. **Manual re-login (default):** System sends a Telegram reminder at 8:00 AM IST. User clicks "Connect Zerodha" in the web app, completes login, and the `request_token` is exchanged for an `access_token`. If not done by 9:15 AM IST, that user's trading is skipped for the day.
2. **TOTP auto-login (optional):** Users who provide their Zerodha TOTP secret (stored encrypted) get headless automated re-authentication at 8:30 AM IST via a browser automation flow.

**Alternatives considered:**
- Fully automated refresh without user interaction (not possible with standard Kite API)
- Requiring manual login with no reminder (poor UX, users would miss it)

**Rationale:**
The manual flow keeps security in user hands — TOTP secrets are sensitive and some users may prefer not to store them. The optional TOTP path provides convenience for users who want fully automated trading without daily intervention.

---

## ADR-005: Partial Fill Handling for Limit Orders

**Status:** Accepted

**Context:**
Limit orders on NSE can be partially filled — the exchange may match only part of the ordered quantity.

**Decision:**
Accept partial fills. When a partial fill is detected: cancel the remaining open portion, place a GTT OCO order for the filled quantity, and mark the position ACTIVE with updated quantity and average entry price. Do not wait for the full quantity to fill.

**Alternatives considered:**
- Wait for full fill (could leave capital tied up indefinitely for illiquid stocks)
- Cancel position on partial fill (wastes the filled position)

**Rationale:**
Accepting partial fills is the most practical approach for swing trading. The position is still valid at the filled price; the GTT protects it correctly. Waiting for full fill is unsuitable for swing trading where entry windows can close quickly.

---

## ADR-006: Swing/Positional Trading with Limit Entry Orders

**Status:** Accepted

**Context:**
Signals include an entry price, stop loss, and target. We needed to decide how orders are placed.

**Decision:**
Place a **limit buy order** at the exact entry price specified in the signal. The order remains open for a user-configurable number of days before being cancelled if not filled.

**Alternatives considered:**
- Market order (immediate fill, price may differ from signal entry)
- GTT entry trigger (entry as a Zerodha trigger; fires when price hits entry)

**Rationale:**
Limit orders give price certainty — the user enters only at their specified price. GTT for entry was considered but adds complexity; a standard limit order with a day expiry is simpler and sufficient for swing trading.

---

## ADR-007: GTT OCO Orders for Stop Loss and Target Management

**Status:** Accepted

**Context:**
After an entry order is filled, stop loss and target need to be managed on the exchange.

**Decision:**
Place a Zerodha **GTT (Good Till Triggered) OCO (One Cancels Other)** order immediately after entry fill. Two legs: stop loss triggers a SL-M sell; target triggers a limit sell. Zerodha manages execution automatically, including when the system is offline.

**Alternatives considered:**
- SL-M order only (no target management)
- System-side price monitoring (backend watches LTP and places orders when levels hit)

**Rationale:**
GTT OCO is the most reliable approach for swing trading. It works even if the server is down and removes the need for continuous price monitoring for exit management. System-side monitoring would require near-real-time LTP feeds and introduces execution risk if the server has downtime.

---

## ADR-008: DB-Tracked Signals with Web Sync UI

**Status:** Accepted

**Context:**
We needed to decide how the system knows which signals are new, modified, or acted on.

**Decision:**
Signals are stored in a dedicated `signals` table. The system polls Google Sheets, diffs against DB state, and records each signal with a stable `source_ref`. The web app provides a Sync Now button and a manual signal entry form. All signal state (ACTIVE, EXPIRED, CANCELLED) is owned by the DB, not the sheet.

**Alternatives considered:**
- Status column written back to the Google Sheet
- Date-based freshness (only today's rows are treated as new)
- Full sheet reconciliation on every poll without DB tracking

**Rationale:**
DB ownership of signal state is more reliable and auditable. Sheet-writeback would require write permissions and creates a two-way sync problem. Date-based freshness is fragile and doesn't support multi-day signals. The Sync Now UI gives the admin manual control without making the sheet the source of truth.

---

## ADR-009: Per-User Configurable Position Sizing

**Status:** Accepted

**Context:**
Different users have different capital sizes, risk appetites, and trading styles.

**Decision:**
Support three position sizing methods, configurable per user:
- `EQUAL` — capital divided equally across max positions
- `FIXED` — fixed rupee amount per trade
- `RISK_BASED` — size so that stop loss hit = fixed % of capital

**Rationale:**
A single sizing method would not suit all users. Making it per-user config keeps the engine generic while accommodating different approaches. All three methods are standard in retail trading.

---

## ADR-010: Signal Change on Active Position — Notify, Don't Auto-Act

**Status:** Accepted

**Context:**
If a signal's entry, stop loss, or target is modified on the sheet while a user has an active position in that stock, we needed to decide what to do.

**Decision:**
Send a Telegram alert to the user flagging the change. Take no automatic action. The user decides via the web app whether to update the GTT, close the position, or ignore the change.

**Alternatives considered:**
- Auto-update the GTT to match new signal values
- Auto-close the position
- Ignore all sheet changes once a position is open

**Rationale:**
Automatic updates to an active position carry significant financial risk if a signal change is erroneous or misinterpreted. Human confirmation is required for any change to an active position. This is the safest default behaviour for a system managing real money.

---

## ADR-011: Duplicate Order Prevention (Three Layers)

**Status:** Accepted

**Context:**
Scheduled jobs running every 5 minutes create a risk of placing duplicate orders if a position is already pending.

**Decision:**
Three layers of protection:
1. DB unique constraint on `(user_id, signal_id)` for non-closed positions
2. Pre-flight check in Portfolio Engine before each order attempt
3. Zerodha `tag` field set to internal `position_id` for reconciliation

**Rationale:**
Defence in depth. The pre-flight check is the primary guard. The DB constraint is the safety net if a race condition bypasses the check. The Zerodha tag enables reconciliation if the system state ever diverges from the broker.

---

## ADR-012: Unmanaged Position Detection and Non-Interference

**Status:** Accepted

**Context:**
Users may manually place trades on Zerodha outside the system. The system must not interfere with these.

**Decision:**
A daily reconciliation job at 9:10 AM IST fetches all Zerodha holdings and compares against tracked positions. Unmanaged holdings trigger a Telegram alert. The core trading loop always skips symbols already present in Zerodha holdings, whether managed or unmanaged. From the web app, users can import unmanaged positions or mark them as intentionally ignored.

**Rationale:**
Non-interference with manual trades is critical to avoid double-buying or accidentally placing conflicting orders. The alert-and-decide pattern keeps humans in control of positions the system did not create.

---

## ADR-013: JWT in httpOnly Cookie for Frontend Auth

**Status:** Accepted

**Context:**
The React frontend needs to authenticate with the Spring Boot API.

**Decision:**
Store JWT in an `httpOnly` cookie (not `localStorage` or `sessionStorage`).

**Alternatives considered:**
- JWT in localStorage (common but vulnerable to XSS)
- Session-based auth with server-side session store

**Rationale:**
`httpOnly` cookies are not accessible to JavaScript, eliminating the XSS token-theft vector. Session-based auth would require a shared session store for future horizontal scaling. JWT in a cookie gives stateless auth with better security than localStorage.

---

## ADR-014: Single VPS Deployment with Docker Compose

**Status:** Accepted

**Context:**
The system serves a small private group and does not need cloud-scale infrastructure.

**Decision:**
Deploy on a single Linux VPS using Docker Compose. Nginx handles TLS termination, serves React static files, and proxies `/api` to Spring Boot. Flyway handles DB migrations on startup.

**Alternatives considered:**
- Kubernetes (overkill for this scale)
- Managed cloud services (higher cost, unnecessary complexity)
- Bare-metal systemd (no containerisation)

**Rationale:**
Docker Compose gives reproducible deployments and easy service management without the overhead of an orchestration platform. A single VPS is sufficient for the trading loop cadence (5-minute intervals) and the small user base. Cost is minimal.

---

## ADR-015: Telegram as the Notification Channel

**Status:** Accepted

**Context:**
Users need real-time alerts for order events, signal changes, and daily summaries.

**Decision:**
Use Telegram with one shared bot. Each user configures their personal `telegram_chat_id`. All notifications are delivered as direct messages. The bot supports read-only commands (`/portfolio`, `/signals`, `/summary`, `/status`). No trading actions are permitted via Telegram.

**Alternatives considered:**
- Email notifications (too slow for trading events)
- WhatsApp (no reliable official bot API)
- Push notifications via mobile app (React Native app is a future item)

**Rationale:**
Telegram has a well-documented bot API, instant delivery, and is widely used by Indian retail traders. Read-only commands prevent accidental or unauthorised trading actions via chat. When the React Native app ships, it can complement Telegram rather than replace it.

---

## ADR-016: Google Sheets API with Service Account

**Status:** Accepted

**Context:**
The signal sheet is hosted on Google Sheets. The system needs read access without requiring user OAuth flows.

**Decision:**
Use Google Sheets API v4 with a **service account**. The admin shares the sheet with the service account email. Credentials are stored as a JSON key file referenced via environment variable.

**Alternatives considered:**
- OAuth 2.0 user credentials (requires browser login flow, token refresh complexity)
- CSV export URL polling (fragile, format-dependent, no diff capability)

**Rationale:**
Service accounts are designed for server-to-server access. No browser login required, no token expiry issues, and the API provides structured row data that enables reliable diffing for signal change detection.
