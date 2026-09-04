# Trade Expectancy / R-Multiple Stats

**Status:** Scoped — see `docs/superpowers/plans/2026-09-03-trade-expectancy-stats.md`
**Tier:** This week
**Origin:** tech-entrepreneur-coach-adhd feature review, 2026-09-03

## What

Originally pitched as "add a win-rate card." Turned out `HistoryPage.tsx` already shows Total Realised P&L, Win/Loss count, and Win Rate % — that check happened during scoping, not during the original review, which is exactly why scoping before building matters.

The real gap: nothing shows R-multiple, expectancy, profit factor, or average win/loss size. Win rate alone doesn't tell you whether the strategy is sound — a 30% win rate with a 4R average win beats a 70% win rate with a 1R average win, and right now there's no way to see that.

## Why it passed the tests

- **ADHD sustainability:** you'll look at this every time you close a trade — direct feedback loop on the thing you're actually trying to improve.
- **Money test:** n/a — internal tool, not productized. Value is decision quality, not revenue.
- **Scope test:** turned out to be entirely frontend — `signalEntryPrice`, `signalStopLoss`, `avgEntryPrice`, `realisedPnl` are already on the `Position` payload. No backend change, no migration. Smaller than the original estimate.

## Data/systems touched

`frontend/src/pages/HistoryPage.tsx`, new `frontend/src/lib/tradeStats.ts`. No backend.

## Open questions for brainstorming

- Should the excluded-trade count (positions with no recorded stop-loss) surface anywhere else, or is a one-line caption on History enough?
- Is R-multiple against the *original* stop-loss (not the breakeven-adjusted one) the right call long-term, or do you want a toggle later?
