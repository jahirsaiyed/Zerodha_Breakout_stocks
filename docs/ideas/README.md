# Feature Ideas

Captured from a tech-entrepreneur-coach-adhd feature review on 2026-09-03. Nothing here is committed to — this is a parking lot for the next brainstorming pass (see `superpowers:brainstorming`).

Each idea file states what it is, why it passed the three tests (ADHD sustainability / money / scope) enough to write down, and open questions to resolve before scoping it into a plan like `docs/superpowers/plans/`.

## This week (small, high-signal)

- [trade-expectancy-stats.md](trade-expectancy-stats.md) — **scoped**, see `docs/superpowers/plans/2026-09-03-trade-expectancy-stats.md`
- [max-drawdown-chart.md](max-drawdown-chart.md)
- [signal-watchlist-state.md](signal-watchlist-state.md)

## This sprint (bigger, still scoped)

- [strategy-performance-breakdown.md](strategy-performance-breakdown.md)
- [backtesting-stub.md](backtesting-stub.md)
- [mobile-push-target-sl-parity.md](mobile-push-target-sl-parity.md)

## Parked

- [parked-ideas.md](parked-ideas.md) — multi-broker support, public SaaS productization, further fill-reconciliation hardening. Explicitly not now; reasoning captured for when it comes up again.

## Observation from the review

The last ~15 commits on this repo before the review were almost all `fix:` on order-fill/GTT reconciliation edge cases. That's valuable work, but it's also the classic pattern of over-hardening infrastructure that already works instead of shipping new user-facing value on a single-user tool. Worth checking during the next brainstorm whether that pattern is recurring.
