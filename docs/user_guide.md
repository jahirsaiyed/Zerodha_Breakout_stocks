---
title: "Zerodha Breakout Stocks — User Guide"
author: "Trading Platform"
date: "2026"
---

# Zerodha Breakout Stocks — User Guide

## Introduction

Zerodha Breakout Stocks is a private, web-based trading dashboard that helps you track breakout signals, manage positions, and automate order placement through your Zerodha account. This guide walks you through every feature of the application from first login to daily trading workflows.

---

## 1. Getting Started

### 1.1 Accessing the Application

Open your browser and navigate to the application URL provided by your administrator (e.g., `https://trading.yourdomain.com`). The application works best in modern browsers: Chrome, Firefox, and Safari.

### 1.2 Logging In

1. Enter your **email address** and **password** on the login screen.
2. Click **Sign In**.
3. If your credentials are correct, you will be taken to the Dashboard.

> **Tip:** If you see "Invalid email or password", check that your Caps Lock is off and try again. Contact your administrator to reset your password if needed.

### 1.3 Logging Out

Click the **Logout** button in the top-right corner of the navigation bar. You will be returned to the login screen and your session will be cleared.

---

## 2. Dashboard

The Dashboard gives you an at-a-glance summary of your trading account.

### 2.1 Summary Cards

| Card | What It Shows |
|------|--------------|
| **Active Positions** | Number of positions currently in the market |
| **Pending Orders** | Positions waiting for entry orders to fill |
| **Total Realised P&L** | Net profit/loss across all closed trades |
| **Win Rate** | Percentage of trades that hit the target |

### 2.2 Active Positions Table

The table shows your current open positions with:

- **Symbol** — NSE stock ticker
- **Qty** — Number of shares held
- **Avg Entry** — Average price at which shares were bought
- **LTP** — Last traded price (refreshed every 30 seconds from Zerodha)
- **Unrealised P&L** — Current floating profit or loss
- **SL / Target** — Stop-loss and target prices from your original signal
- **GTT** — Whether a GTT OCO order is active on Zerodha
- **Status** — Current position status badge

> **Unrealised P&L** is only shown when your Zerodha account is connected. If Zerodha is not connected, LTP and P&L columns show a dash (—).

---

## 3. Signals

Signals are your trading ideas — each signal defines an entry price, stop-loss, and target for a specific stock.

### 3.1 Viewing Signals

Navigate to **Signals** from the left sidebar. Active signals are listed first, followed by expired or cancelled signals.

Each signal row shows:

- **Symbol** — NSE stock code
- **Entry / Stop Loss / Target** — Price levels in ₹
- **R:R** — Risk-to-reward ratio (higher is better)
- **LTP** — Last traded price fetched from Google Finance (ACTIVE signals only)
- **vs Entry** — How far the current LTP is above or below your entry price, shown as a colour-coded badge (ACTIVE signals only):
  - 🔴 **Red** — LTP is below entry price (stock hasn't reached your entry yet, or has pulled back)
  - 🟡 **Amber** — LTP is 0–5% above entry (close to entry, still a reasonable chase)
  - 🟢 **Green** — LTP is more than 5% above entry (stock has broken out strongly)
- **Source** — *Manual* (added by you) or *Sheet* (synced from Google Sheets)
- **Status** — ACTIVE, EXPIRED, or CANCELLED

### 3.2 Adding a Signal Manually

1. Click **+ Add Signal**.
2. Fill in the form:
   - **Symbol** — NSE ticker (e.g., RELIANCE, INFY, TATAMOTORS)
   - **Entry Price** — Price at which you want to buy
   - **Stop Loss** — Maximum loss price (must be below Entry)
   - **Target** — Profit-taking price (must be above Entry)
   - **Notes** — Optional remarks
3. Click **Add Signal**.

> **Validation:** Entry price must be above stop loss, and target must be above entry. The system will show an error if these conditions are not met.

### 3.3 Editing a Signal

You can edit an ACTIVE signal that has no open positions against it:

1. Click **Edit** on the signal row.
2. Modify the entry price, stop loss, target, or notes inline.
3. Click **Save**.

### 3.4 Cancelling a Signal

Click **Cancel** on any ACTIVE signal to mark it as CANCELLED. Cancelled signals will no longer be used for new position entries.

### 3.5 Syncing from Google Sheets

If a Google Sheet is configured by your administrator:

1. Click **Sync Now** in the top-right of the Signals page.
2. A banner will confirm how many signals were added, modified, or removed.

The system also auto-syncs on a daily schedule.

---

## 4. Positions

The Positions page shows all your current holdings and pending entries.

### 4.1 Tabs

| Tab | Shows |
|-----|-------|
| **Active** | Positions currently in the market |
| **Pending** | Positions waiting for entry limit orders to fill |

### 4.2 Active Positions

For each active position you can see:

- Live LTP and unrealised P&L (requires Zerodha connection)
- GTT status badge (whether target + SL protection is active)
- **Close** button for a manual market exit

### 4.3 Manual Close

To close a position before it hits its target or stop-loss:

1. Click **Close** on the position row.
2. A confirmation prompt appears: "Confirm close?"
3. Click **Yes** to place a CNC market sell order through Zerodha immediately.
4. Click **No** to dismiss.

> **Note:** The market sell is placed at the current market price. In fast-moving markets, the actual fill price may differ from the LTP shown.

### 4.4 Pending Positions

Pending positions have entry limit orders placed but not yet filled. They will move to **Active** once Zerodha confirms the order fill. The scheduler checks fill status regularly during market hours.

### 4.5 Cancelling a Pending Entry

To cancel a pending entry before it fills:

1. Click **Cancel** on the pending position row.
2. A confirmation prompt appears: "Confirm cancel?"
3. Click **Yes** — the system will cancel the entry order on Zerodha and mark the position as CANCELLED.
4. Click **No** to dismiss.

> **Note:** If the entry order fills on Zerodha in the brief window between you clicking Cancel and the cancellation reaching Zerodha, the position will transition to Active normally. In that case, use the **Close** button on the Active tab instead.

---

## 5. Orders

Navigate to **Orders** to see every order placed by the system on your behalf.

### 5.1 Order Types

| Type | Meaning |
|------|---------|
| **Entry** | Limit buy order placed when a signal triggered |
| **Exit (Target)** | Sell order placed when target price was hit |
| **Exit (SL)** | Sell order placed when stop-loss was hit |
| **Exit (Manual)** | Market sell order placed by you via the Exit button |

### 5.2 Order Statuses

| Status | Meaning |
|--------|---------|
| **PENDING** | Order placed, awaiting fill |
| **FILLED** | Order fully executed |
| **CANCELLED** | Order cancelled (expired or manually cancelled) |
| **REJECTED** | Order rejected by Zerodha |

### 5.3 Pagination

The orders list loads 50 orders at a time (newest first). Add `?page=1&size=50` to the URL for older pages if needed.

---

## 6. History

The History page shows all your **closed** positions and overall trading performance.

### 6.1 Summary Cards

- **Total Realised P&L** — Net profit/loss across all closed trades
- **Win / Loss** — Count of target-hit vs stop-loss-hit trades
- **Win Rate** — Percentage of winning trades

### 6.2 Cumulative P&L Chart

A line chart shows your running P&L over time. Each point represents a closed trade. A green line indicates overall profitability; red indicates a drawdown.

The chart appears once you have more than one closed trade.

### 6.3 Filtering Trades

Use the filter pills to narrow the table:

| Filter | Shows |
|--------|-------|
| **All** | All closed trades |
| **Target Hit** | Trades that hit the target (winners) |
| **Stop Loss** | Trades stopped out (losers) |
| **Manual Exit** | Trades exited manually |

### 6.4 Trade Table

Each row shows the symbol, quantity, average entry price, realised P&L, outcome badge, and the date the trade was closed.

---

## 7. Settings

Access Settings from the sidebar to configure your trading preferences and broker connection.

### 7.0 Account Overview

At the top of the Settings page is a read-only Account Overview panel that gives you a quick snapshot of your account state:

| Metric | What It Shows |
|--------|--------------|
| **Available Margin** | Cash available in your Zerodha account for new trades (requires Zerodha connection) |
| **Positions Used** | Your current active positions vs. your configured maximum (e.g. 2 / 5) |
| **Open P&L** | Total unrealised profit/loss across all active positions (requires Zerodha connection) |

This panel refreshes every 60 seconds automatically. If Zerodha is not connected, margin and Open P&L show a dash (—).

### 7.1 Trading Configuration

| Setting | Description |
|---------|-------------|
| **Max Positions** | Maximum number of simultaneous open positions (1–50) |
| **Order Expiry Days** | Cancel unfilled entry orders after N days |
| **Position Sizing Method** | How capital is allocated per trade |
| **Sizing Value** | Amount in ₹ (Fixed/Equal) or % of capital (Risk-Based) |

**Position Sizing Methods:**

- **Fixed Amount** — Each position gets a fixed ₹ amount (e.g., ₹10,000 per trade)
- **Equal Split** — Capital is divided equally across all max positions
- **Risk-Based (%)** — Capital per trade is sized so that the stop-loss distance equals N% of your total capital

Click **Save Changes** to apply.

### 7.2 Connecting Zerodha

Zerodha integration enables automatic order placement and live market prices.

**To connect:**

1. Enter your **Zerodha API Key** in the field provided.
2. Enter your **Zerodha API Secret**.
3. Optionally enter your **TOTP Secret** (the seed used by your 2FA authenticator app) to enable automatic TOTP code generation.
4. Click **Save Changes**.
5. Click **Connect Zerodha** — you will be redirected to the Zerodha login page.
6. Log in to Zerodha and authorise the app.
7. You will be redirected back to Settings with a "Zerodha connected successfully" confirmation.

> **Security:** Your API secret and TOTP secret are stored encrypted in the database. They are never transmitted to the browser after being saved.

**To disconnect:**

Click **Disconnect** next to the Connected badge. This clears the stored access token. You can reconnect at any time.

**Zerodha access tokens expire daily** — you must reconnect after each trading session, or your administrator can set up an automatic re-login flow using the TOTP secret.

### 7.3 Telegram Notifications

Receive trade alerts on Telegram:

1. Find your **Telegram Chat ID** (message @userinfobot on Telegram).
2. Enter your Chat ID in the field.
3. Click **Save Changes**.
4. Click **Send Test** to verify the bot can reach you.

You will receive notifications for:

- Entry order placed
- Entry order filled (position opened)
- Target hit (position closed profitably)
- Stop-loss hit (position closed at a loss)
- Daily P&L summary at 3:45 PM IST

### 7.4 Changing Your Password

Scroll to the **Change Password** section (separate from the main settings form):

1. Enter your **Current Password**.
2. Enter your **New Password** (minimum 8 characters).
3. Enter your new password again in **Confirm New Password**.
4. Click **Change Password**.

---

## 8. Telegram Bot Commands

If Telegram is enabled and you have configured your Chat ID, you can send commands directly to the trading bot from the Telegram app.

| Command | What It Returns |
|---------|----------------|
| `/portfolio` | Your active and pending positions |
| `/signals` | List of all active signals with R:R ratios |
| `/summary` | Total closed trades, win/loss count, and net P&L |
| `/status` | Bot online status and current time |

Commands from Telegram IDs not linked to an account are silently ignored.

---

## 9. Admin Features

Admin users have access to an additional **Admin** section in the sidebar.

### 9.1 System Health

The health panel shows:

- **Instrument Cache** — Whether NSE symbols are loaded and how many
- **Last Sheet Sync** — Time and results of the most recent Google Sheets sync
- **Zerodha Status** — Which users are currently connected to Zerodha

### 9.2 User Management

Admins can:

- **Create users** — Click **+ New User**, fill in name, email, password, and role
- **Enable/Disable users** — Click Enable or Disable on any user row (you cannot disable your own account)
- **Assign roles** — USER (standard trader) or ADMIN (full access)

---

## 10. Daily Workflow

Here is a typical daily trading workflow:

1. **Morning (before market open, 9:00 AM IST)**
   - Log in and check the Dashboard for overnight GTT status
   - Review active signals on the Signals page
   - Reconnect Zerodha if the access token has expired (Settings → Connect Zerodha)

2. **Signal management**
   - Click **Sync Now** on the Signals page if signals come from a Google Sheet
   - Add any manual signals you identified through your analysis
   - The portfolio engine automatically places entry limit orders for eligible signals during market hours

3. **During market hours**
   - Monitor the Positions page for fills and live P&L
   - The system auto-refreshes every 30 seconds
   - Use the **Close** button on the Active tab to exit a position manually before the target or stop-loss is triggered
   - Use the **Cancel** button on the Pending tab to withdraw an unfilled entry order

4. **End of day (after 3:30 PM IST)**
   - Check the History page for the day's closed trades and P&L
   - The Telegram bot sends a daily summary at 3:45 PM IST

---

## 11. Frequently Asked Questions

**Q: Why is my LTP showing a dash (—)?**
A: Your Zerodha account is not connected. Go to Settings and click Connect Zerodha to authorise your session.

**Q: My Zerodha access token expired — what do I do?**
A: Go to Settings and click Connect Zerodha to start a new OAuth session. Access tokens expire at midnight IST daily, which is a Zerodha requirement.

**Q: Can I add a signal for a stock not on NSE?**
A: The system validates symbols against the NSE instrument cache. If validation is not loaded, it will accept any symbol. Contact your admin if a valid symbol is being rejected.

**Q: Why was my entry order not placed?**
A: Common reasons include: Zerodha not connected, insufficient funds in your Zerodha account, max positions limit reached (check Settings), or the order expiry window passed (check the Orders page for a CANCELLED order).

**Q: What happens if Zerodha is down when a target/SL is hit?**
A: GTT OCO orders are placed directly on Zerodha's servers and execute independently of this application — even if the app is offline. The app reconciles the status on the next sync.

**Q: How do I get my Telegram Chat ID?**
A: Open Telegram and send a message to **@userinfobot**. It will reply with your numeric Chat ID.

**Q: Can multiple users share the same Zerodha account?**
A: No. Each user must connect their own individual Zerodha account. Positions and orders are tracked per user.

---

## 12. Troubleshooting

| Issue | Solution |
|-------|----------|
| Blank screen on load | Hard-refresh (Ctrl+Shift+R). Check with admin if backend is running. |
| "Session expired" on Zerodha callback | Start the Connect flow again — the OAuth window is valid for 10 minutes only. |
| Orders placed but not showing in Orders page | Refresh the page. Orders refresh every 30 seconds automatically. |
| Telegram test message not received | Verify your Chat ID is correct. Ensure the Telegram bot token is configured by your admin. |
| P&L numbers not updating | Live quotes only update when Zerodha is connected. Check Settings. |

---

*For technical issues or account problems, contact your system administrator.*
