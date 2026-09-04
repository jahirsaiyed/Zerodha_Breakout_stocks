# Backtesting Stub

**Status:** Idea — not scoped
**Tier:** This sprint
**Origin:** tech-entrepreneur-coach-adhd feature review, 2026-09-03

## What

Even a crude backtest — "if every ACTIVE/EXPIRED signal from the Sheet had been taken at its recorded entry/stop/target, what's the resulting equity curve" — would answer a more important question than any live-tracking feature: is the underlying strategy worth the engineering effort currently going into fill-tracking and GTT reconciliation.

## Why it passed the tests

- **ADHD sustainability:** high — directly resolves the uncertainty behind why you're building any of this in the first place.
- **Money test:** n/a — internal tool.
- **Scope test:** genuinely uncertain, and probably the riskiest idea in this set to scope confidently without a spike first. Needs: historical daily/hourly closes for every symbol that ever had a signal (source unclear — Zerodha historical API? something else?), and a decision on how closely to model the real position-sizing/margin-cap logic vs. a simplified flat-size assumption.

## Data/systems touched

Unknown until a spike happens. Likely a new read-only module, not touching live trading paths. Needs a historical price data source decided first — that's the actual scoping blocker, not the computation itself.

## Open questions for brainstorming

- What's the historical OHLC data source? Zerodha's historical candle API has rate limits and paid-tier gating — worth checking before assuming it's free/available.
- Full backtest engine (position sizing, margin caps, multi-position concurrency) vs. a much simpler "one signal at a time, flat position size" first pass?
- Is this really "this sprint" scope, or does it need its own spike/plan before a size estimate means anything?
