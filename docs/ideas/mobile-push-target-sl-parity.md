# Mobile Push Parity for Target/Stop-Loss Hits

**Status:** Idea — not scoped
**Tier:** This sprint
**Origin:** tech-entrepreneur-coach-adhd feature review, 2026-09-03

## What

Telegram already fires notifications for entry fills, target hits, stop-loss triggers, and daily summaries (`com.trading.notifications.PortfolioEventListener`). The mobile app already has push notification plumbing (`mobile/hooks/usePushNotifications.ts`, device token registration, Firebase). Target/SL-hit events don't currently appear to reach mobile push — wiring them through the same event listener that already fires Telegram messages would mostly be reuse, not new infrastructure.

## Why it passed the tests

- **ADHD sustainability:** n/a directly, but closes an inconsistency between two channels that already exist.
- **Money test:** n/a — internal tool.
- **Scope test:** looks small because the event-listener pattern and the device-token/Firebase plumbing both already exist — but needs verification that `PortfolioEventListener` (or wherever Telegram sends fire) can cleanly also call the push-notification path without duplicating logic.

## Data/systems touched

`backend/src/main/java/com/trading/notifications/PortfolioEventListener.java` (or wherever Telegram sends originate), whatever push-sending service already backs `mobile/hooks/usePushNotifications.ts` device registration.

## Open questions for brainstorming

- Does a backend push-sending service already exist (paralleling `NotificationService`/`TelegramBotService`), or does mobile push currently only cover registration with no send path wired up yet? Check before assuming this is "just wiring."
- Should push and Telegram be independently toggleable per user, or always both-or-neither?
