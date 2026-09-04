# Max Drawdown on the P&L Chart

**Status:** Idea — not scoped
**Tier:** This week
**Origin:** tech-entrepreneur-coach-adhd feature review, 2026-09-03

## What

`HistoryPage.tsx` already builds a cumulative P&L series (`chartData` with `cumPnl` running total) for the `AreaChart`. Add a derived max-drawdown series/marker: the largest peak-to-trough decline in that cumulative line, shown as a stat and/or a shaded region on the chart.

## Why it passed the tests

- **ADHD sustainability:** cheap to check, direct extension of something you already look at.
- **Money test:** n/a — internal tool.
- **Scope test:** looks like a pure derived-value addition over data already computed client-side — likely a single-file frontend change, similar shape to the R-multiple work.

## Data/systems touched

Likely just `frontend/src/pages/HistoryPage.tsx` (or a shared `lib/tradeStats.ts` if it grows there instead). No backend expected.

## Open questions for brainstorming

- Show it as a single "Max Drawdown: ₹X" stat card, or shade the drawdown period on the chart itself (more visual, more chart-library work)?
- Drawdown in ₹ or in R (consistent with the expectancy work)?
