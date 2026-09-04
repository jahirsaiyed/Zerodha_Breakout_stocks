# Per-Signal-Source / Strategy Performance Breakdown

**Status:** Idea — not scoped
**Tier:** This sprint
**Origin:** tech-entrepreneur-coach-adhd feature review, 2026-09-03

## What

Signals come from a Google Sheet (`source: 'GOOGLE_SHEET'`) or are added manually (`source: 'MANUAL'`), and each has a `sourceRef`. Breaking win-rate/expectancy down by source (or by whatever tag distinguishes strategies in the Sheet, if any) would show whether the edge is coming from one strategy/source or spread evenly — the single highest-leverage feature for actually improving the trading system, since it evaluates the strategy rather than just tracking outcomes.

## Why it passed the tests

- **ADHD sustainability:** directly answers "is what I'm doing working," which is the question a trader building this system should actually care most about.
- **Money test:** n/a — internal tool. But this is the feature most likely to change real trading decisions.
- **Scope test:** not yet verified. Depends on whether the Sheet already encodes a strategy/tag per signal (`sourceRef` might already carry this) or whether that requires a Sheet schema change plus a signals-table column plus a sync-service change — a much bigger lift than it looks.

## Data/systems touched

`backend/src/main/java/com/trading/signals/` (possibly a new column if no strategy tag exists yet), `SheetSyncService`, and a new aggregation either in `PortfolioEngine`/a new service, or client-side over already-fetched positions+signals if `sourceRef` is granular enough.

## Open questions for brainstorming

- Does `sourceRef` already carry enough signal (e.g. a strategy name or sheet-row tag) to group by, or does the Sheet need a new column first?
- Is this reusing `docs/superpowers/plans/2026-09-03-trade-expectancy-stats.md`'s R-multiple math, just grouped by source instead of aggregated flat?
- Worth prototyping this as a client-side group-by over the existing `/positions` + `/signals` data before touching the backend at all.
