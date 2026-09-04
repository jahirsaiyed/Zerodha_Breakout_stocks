# Parked Ideas

**Status:** Explicitly deferred, not scoped
**Origin:** tech-entrepreneur-coach-adhd feature review, 2026-09-03

Bundled together because each failed one of the three tests (ADHD sustainability / money / scope) clearly enough that they don't need individual scoping files yet. Revisit if circumstances change (e.g. you actually decide to productize this).

## Multi-broker support (Upstox, Angel One, etc.)

Fails the scope test as things stand: no second user asking for it, and it multiplies the surface area of every broker-specific integration point (`backend/src/main/java/com/trading/broker/`, `zerodha/`) for a benefit only realized if you're serving other traders on other brokers.

## Public-facing SaaS productization

The README states "Private — all rights reserved," and there's no evidence in the codebase or conversation of an active decision to monetize this. Retrofitting multi-tenancy/billing onto a single-user tool is a multi-month detour, not a 2-week feature — fails the scope test hard. Revisit only if there's an actual decision to open this up, not as a default assumption.

## Further fill-reconciliation hardening

Not a rejected *feature* so much as a flagged pattern: the ~15 commits before this review were almost all `fix:` on order-fill/GTT reconciliation edge cases. More hardening here, absent a specific bug someone hit, is the classic big-tech trap of polishing infrastructure that already works instead of shipping new value on a tool with one user. Fix real bugs as they occur; don't proactively harden further.
