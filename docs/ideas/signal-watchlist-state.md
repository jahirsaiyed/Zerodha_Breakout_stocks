# Signal Watchlist State

**Status:** Idea — not scoped
**Tier:** This week
**Origin:** tech-entrepreneur-coach-adhd feature review, 2026-09-03

## What

`SignalsPage.tsx` currently doesn't distinguish "signal synced from the Sheet but not yet actionable" from "entry order already placed" — everything with `status: 'ACTIVE'` looks the same regardless of whether a position exists for it. A filter/badge for "watching" vs. "in position" would close that gap.

## Why it passed the tests

- **ADHD sustainability:** solves a problem noticed from actually trading with the tool daily, not a hypothetical.
- **Money test:** n/a — internal tool.
- **Scope test:** unverified — needs a look at how `SignalsPage.tsx` currently joins signals to positions (or whether it needs a new join at all) before this can be scoped for real.

## Data/systems touched

Likely `frontend/src/pages/SignalsPage.tsx`; may need to know, per signal, whether an open `Position` references it (`Position.signalId`). Check whether that join already happens somewhere (e.g. `GET /api/portfolio/positions/live`) before assuming a backend change is needed.

## Open questions for brainstorming

- Is this a client-side derived state (cross-reference the signals list against the positions list, both already fetched elsewhere) or does it need a backend field?
- What are the actual states worth surfacing — just "watching / in position", or also "expired", "order pending fill"?
