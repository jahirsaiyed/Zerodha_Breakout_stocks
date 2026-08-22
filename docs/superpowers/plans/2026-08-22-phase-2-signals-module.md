# Phase 2: Signals Module — Implementation Plan

**Date:** 2026-08-22
**Branch:** phase-2-signals
**Base:** phase-1-foundation (HEAD: ad8274e)

## Status

- [x] Task 1: Signal JPA entities + repositories
- [x] Task 2: Signals service + REST API
- [x] Task 3: SignalSyncLog API + tests

## Notes

DB schema already complete in V1__initial_schema.sql (scaffolded in Phase 1 Task 1).
Tables: signals, positions, orders, signal_sync_log.

## Task 1: Signal JPA Entities + Repositories

### Enums (top-level, in `com.trading.signals`)

- `SignalSource`  — GOOGLE_SHEET, MANUAL
- `SignalStatus`  — ACTIVE, EXPIRED, CANCELLED
- `PositionStatus` — PENDING_ENTRY, ACTIVE, CANCELLED, CLOSED_TARGET, CLOSED_SL, CLOSED_MANUAL
- `OrderType`     — ENTRY, EXIT_TARGET, EXIT_SL
- `OrderKind`     — LIMIT, GTT_OCO, MARKET
- `OrderStatus`   — PENDING, FILLED, CANCELLED, REJECTED

### Entities

**Signal** (`com.trading.signals.Signal`)
- id, symbol, entryPrice, stopLoss, target, riskRewardRatio (computed)
- source (SignalSource), sourceRef, status (SignalStatus)
- notes, addedAt, updatedAt

**Position** (`com.trading.signals.Position`)
- id, user (ManyToOne), signal (ManyToOne), symbol, quantity
- avgEntryPrice, entryOrderId, gttOrderId
- status (PositionStatus), openedAt, closedAt, realisedPnl

**Order** (`com.trading.signals.Order`)
- id, user (ManyToOne), position (ManyToOne), zerodhaOrderId
- type (OrderType), orderKind (OrderKind), symbol, quantity, price
- status (OrderStatus), placedAt, updatedAt

**SignalSyncLog** (`com.trading.signals.SignalSyncLog`)
- id, syncedAt, source (SignalSource)
- signalsAdded, signalsModified, signalsRemoved, notes

### Repositories

- `SignalRepository` — findByStatus, findBySymbol
- `PositionRepository` — findByUserIdAndStatus
- `OrderRepository` — findByPositionId
- `SignalSyncLogRepository` — findTopNOrderBySyncedAtDesc (Pageable)

## Task 2: Signals Service + REST API

### SignalService methods

- `list(SignalStatus status)` — filter by status, null = all
- `create(CreateSignalRequest)` — validates price logic, computes R:R, saves
- `update(Long id, UpdateSignalRequest)` — only ACTIVE signals with no ACTIVE/PENDING positions
- `cancel(Long id)` — sets status CANCELLED, same guard as update

### Validation rules

- entry_price > stop_loss (ValidationException → 400)
- target > entry_price (ValidationException → 400)
- R:R = (target - entry) / (entry - stop_loss), scale 4dp

### SignalController endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /signals | USER | List signals (optional ?status=ACTIVE) |
| POST | /signals | USER | Create manual signal |
| PUT | /signals/{id} | USER | Update ACTIVE signal |
| DELETE | /signals/{id} | USER | Cancel signal |
| GET | /signals/sync-log | USER | Last 50 sync log entries |

### DTOs

- `SignalResponse` — all fields + computed riskRewardRatio
- `CreateSignalRequest` — symbol, entryPrice, stopLoss, target, notes (optional)
- `UpdateSignalRequest` — entryPrice, stopLoss, target, notes (all optional, partial update)

## Task 3: Tests

### Unit tests (SignalServiceTest)

- create: valid input → saved with correct R:R
- create: entry <= stopLoss → throws
- create: target <= entry → throws
- update: ACTIVE signal, no active position → updated
- update: signal not found → throws
- cancel: ACTIVE signal → CANCELLED
- cancel: already CANCELLED → no-op or throws

### Integration tests (SignalControllerIT)

- GET /signals → 200, list
- POST /signals valid → 201, signal created
- POST /signals invalid prices → 400
- PUT /signals/{id} → 200, updated
- DELETE /signals/{id} → 200, cancelled
- GET /signals/sync-log → 200, list

## Accepted Deferred Items

- Google Sheets sync scheduler (Phase 3)
- Position/Order CRUD APIs (Phase 3, driven by portfolio engine)
- Frontend signals page (Phase 4)
