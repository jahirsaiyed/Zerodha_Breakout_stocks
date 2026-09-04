# Trade Expectancy Stats (R-Multiple / Expectancy Card) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the History page's summary stats beyond raw win/loss counts with trade-quality metrics that matter for evaluating the strategy itself: R-multiple per trade, expectancy (R), profit factor, and average win/loss size.

**Correction from the original feature idea:** The initial suggestion was "add a win-rate card" — but `HistoryPage.tsx` already shows Total Realised P&L, Win/Loss count, and Win Rate %. The real gap is R-multiple / expectancy, which nobody has built. This plan scopes that gap only.

**Architecture:** Pure frontend change. `GET /api/portfolio/positions` already returns `signalEntryPrice`, `signalStopLoss`, `avgEntryPrice`, `quantity`, and `realisedPnl` on every `Position` — everything needed to compute R-multiple is already on the wire. No backend change, no migration.

**Tech Stack:** React 19 · TanStack Query v5 (existing `useQuery(['positions'])`) · Vitest · TypeScript

## Global Constraints

- No backend changes — this task must not touch `backend/`.
- New pure logic goes in `frontend/src/lib/tradeStats.ts` (new file) so it's unit-testable without rendering the page.
- Keep the new UI inline in `HistoryPage.tsx` — it's used in exactly one place; don't extract a component for it (YAGNI).
- Use the **original** `signalStopLoss` as the risk baseline, not `breakevenSl`. If the stop was moved to breakeven mid-trade, expectancy should still reflect the risk that was actually taken when the position was opened, not the risk after it was reduced.
- Frontend tests: Vitest, colocated in `frontend/src/test/`, pattern from `frontend/src/test/LoginPage.test.tsx`.
- Types already defined in `frontend/src/lib/types.ts` — do not redefine `Position` locally.

---

### Task 1: R-multiple / expectancy calculation module

**Files:**
- Create: `frontend/src/lib/tradeStats.ts`
- Create: `frontend/src/test/tradeStats.test.ts`

**Interfaces:**

```typescript
// frontend/src/lib/tradeStats.ts
import type { Position } from './types'

export interface TradeStats {
  tradeCount: number          // closed, non-cancelled trades used for R stats (valid risk baseline only)
  excludedCount: number       // closed trades skipped because entry/stop/risk was invalid or missing
  expectancyR: number | null  // mean R-multiple across included trades; null if tradeCount === 0
  avgWinR: number | null
  avgLossR: number | null
  profitFactor: number | null // grossProfit / grossLoss in ₹; null if grossLoss === 0 and grossProfit === 0
  avgWinAmount: number | null // ₹
  avgLossAmount: number | null // ₹
}

export function computeTradeStats(positions: Position[]): TradeStats
```

- [ ] **Step 1: Write the failing test first**

```typescript
// frontend/src/test/tradeStats.test.ts
import { describe, it, expect } from 'vitest'
import { computeTradeStats } from '../lib/tradeStats'
import type { Position } from '../lib/types'

function pos(overrides: Partial<Position>): Position {
  return {
    id: 1, symbol: 'TEST', quantity: 10, avgEntryPrice: 100,
    entryOrderId: null, gttOrderId: null, status: 'CLOSED_TARGET',
    openedAt: '2026-01-01T00:00:00Z', closedAt: '2026-01-02T00:00:00Z',
    realisedPnl: 0, signalId: 1, signalEntryPrice: 100, signalStopLoss: 95,
    signalTarget: 115, breakevenSl: null,
    ...overrides,
  }
}

describe('computeTradeStats', () => {
  it('computes expectancy across a mix of wins and losses', () => {
    // risk = (100 - 95) * 10 = 50 per trade
    const win = pos({ realisedPnl: 100 })   // R = +2
    const loss = pos({ realisedPnl: -50, status: 'CLOSED_SL' }) // R = -1
    const stats = computeTradeStats([win, loss])
    expect(stats.tradeCount).toBe(2)
    expect(stats.expectancyR).toBeCloseTo(0.5) // mean(2, -1)
    expect(stats.avgWinR).toBeCloseTo(2)
    expect(stats.avgLossR).toBeCloseTo(-1)
  })

  it('excludes trades with no recorded stop-loss instead of throwing', () => {
    const noStop = pos({ realisedPnl: 100, signalStopLoss: null })
    const stats = computeTradeStats([noStop])
    expect(stats.tradeCount).toBe(0)
    expect(stats.excludedCount).toBe(1)
    expect(stats.expectancyR).toBeNull()
  })

  it('excludes trades where entry equals stop (zero risk, div-by-zero guard)', () => {
    const zeroRisk = pos({ realisedPnl: 10, signalEntryPrice: 100, signalStopLoss: 100 })
    const stats = computeTradeStats([zeroRisk])
    expect(stats.excludedCount).toBe(1)
  })

  it('ignores CANCELLED and non-closed positions', () => {
    const cancelled = pos({ status: 'CANCELLED', realisedPnl: null })
    const pending = pos({ status: 'ACTIVE', realisedPnl: null, closedAt: null })
    const stats = computeTradeStats([cancelled, pending])
    expect(stats.tradeCount).toBe(0)
    expect(stats.excludedCount).toBe(0) // not closed trades at all, not "excluded" — just not in scope
  })

  it('reports profitFactor as null when there are no losses to divide by', () => {
    const win = pos({ realisedPnl: 100 })
    const stats = computeTradeStats([win])
    expect(stats.profitFactor).toBeNull()
  })

  it('returns all-null stats for an empty list', () => {
    const stats = computeTradeStats([])
    expect(stats.tradeCount).toBe(0)
    expect(stats.expectancyR).toBeNull()
    expect(stats.profitFactor).toBeNull()
  })
})
```

- [ ] **Step 2: Run it — confirm it fails** (`npm test -- tradeStats` from `frontend/`) because `tradeStats.ts` doesn't exist yet.

- [ ] **Step 3: Implement `computeTradeStats`**

```typescript
// frontend/src/lib/tradeStats.ts
import type { Position } from './types'

export interface TradeStats {
  tradeCount: number
  excludedCount: number
  expectancyR: number | null
  avgWinR: number | null
  avgLossR: number | null
  profitFactor: number | null
  avgWinAmount: number | null
  avgLossAmount: number | null
}

const CLOSED_STATUSES = new Set(['CLOSED_TARGET', 'CLOSED_SL', 'CLOSED_MANUAL'])

function mean(values: number[]): number | null {
  return values.length ? values.reduce((s, v) => s + v, 0) / values.length : null
}

export function computeTradeStats(positions: Position[]): TradeStats {
  const closed = positions.filter(p => CLOSED_STATUSES.has(p.status) && p.realisedPnl != null)

  let excludedCount = 0
  const rMultiples: number[] = []
  const amounts: number[] = []

  for (const p of closed) {
    const entry = p.signalEntryPrice
    const stop = p.signalStopLoss
    const pnl = p.realisedPnl as number

    amounts.push(pnl)

    if (entry == null || stop == null) {
      excludedCount++
      continue
    }
    const riskPerShare = Math.abs(entry - stop)
    if (riskPerShare === 0) {
      excludedCount++
      continue
    }
    const riskAmount = riskPerShare * p.quantity
    rMultiples.push(pnl / riskAmount)
  }

  const wins = rMultiples.filter(r => r > 0)
  const losses = rMultiples.filter(r => r < 0)
  const winAmounts = amounts.filter(a => a > 0)
  const lossAmounts = amounts.filter(a => a < 0)

  const grossProfit = winAmounts.reduce((s, a) => s + a, 0)
  const grossLoss = Math.abs(lossAmounts.reduce((s, a) => s + a, 0))

  return {
    tradeCount: rMultiples.length,
    excludedCount,
    expectancyR: mean(rMultiples),
    avgWinR: mean(wins),
    avgLossR: mean(losses),
    profitFactor: grossLoss > 0 ? grossProfit / grossLoss : null,
    avgWinAmount: mean(winAmounts),
    avgLossAmount: mean(lossAmounts),
  }
}
```

- [ ] **Step 4: Run tests again — confirm green.**

---

### Task 2: Wire the stats into `HistoryPage.tsx`

**Files:**
- Modify: `frontend/src/pages/HistoryPage.tsx`

**Steps:**

- [ ] **Step 1:** Import `computeTradeStats` and call it once from the already-loaded `positions`:

```typescript
import { computeTradeStats } from '../lib/tradeStats'
// ...
const tradeStats = computeTradeStats(positions)
```

- [ ] **Step 2:** Add a second row of stat cards below the existing 3-card grid (Total P&L / Win-Loss / Win Rate), reusing the same card markup style:

```tsx
{tradeStats.tradeCount > 0 && (
  <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <p className="text-xs text-gray-500">Expectancy</p>
      <p className={`mt-1 text-xl font-semibold ${(tradeStats.expectancyR ?? 0) >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>
        {tradeStats.expectancyR! >= 0 ? '+' : ''}{tradeStats.expectancyR!.toFixed(2)}R
      </p>
    </div>
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <p className="text-xs text-gray-500">Profit Factor</p>
      <p className="mt-1 text-xl font-semibold text-gray-900">
        {tradeStats.profitFactor != null ? tradeStats.profitFactor.toFixed(2) : '—'}
      </p>
    </div>
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <p className="text-xs text-gray-500">Avg Win / Avg Loss</p>
      <p className="mt-1 text-xl font-semibold text-gray-900">
        <span className="text-emerald-600">₹{(tradeStats.avgWinAmount ?? 0).toFixed(0)}</span>
        <span className="mx-1 text-gray-300">/</span>
        <span className="text-red-500">₹{Math.abs(tradeStats.avgLossAmount ?? 0).toFixed(0)}</span>
      </p>
    </div>
  </div>
)}
{tradeStats.excludedCount > 0 && (
  <p className="-mt-4 mb-6 text-xs text-gray-400">
    {tradeStats.excludedCount} closed trade{tradeStats.excludedCount === 1 ? '' : 's'} excluded from R-based stats (no recorded stop-loss)
  </p>
)}
```

- [ ] **Step 3:** Manually verify in the browser (`npm run dev` in `frontend/`, log in, go to History) with real closed positions — confirm the numbers look sane against a couple of trades you can check by hand.

---

## Definition of Done

- [ ] `npm test` passes in `frontend/` including the new `tradeStats.test.ts`
- [ ] `npm run build` (or `tsc --noEmit`) passes with no type errors
- [ ] History page renders the new row only when there's at least one trade with valid R data; shows nothing extra when the account has no closed trades yet (empty state unaffected)
- [ ] Manually spot-checked against real data in the browser
