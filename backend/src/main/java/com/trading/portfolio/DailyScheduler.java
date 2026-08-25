package com.trading.portfolio;

import com.trading.notifications.NotificationService;
import com.trading.signals.Position;
import com.trading.signals.PositionRepository;
import com.trading.signals.PositionStatus;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * IST-zoned daily jobs:
 * <ul>
 *   <li>08:00 — Telegram re-login reminder for users without a valid Zerodha token</li>
 *   <li>15:45 — Per-user daily P&amp;L summary via Telegram</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyScheduler {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final List<PositionStatus> CLOSED_STATUSES = List.of(
            PositionStatus.CLOSED_TARGET,
            PositionStatus.CLOSED_SL,
            PositionStatus.CLOSED_MANUAL
    );

    private final UserConfigRepository userConfigRepository;
    private final PositionRepository   positionRepository;
    private final NotificationService  notificationService;

    // ── 8:00 AM IST — Zerodha re-login reminder ──────────────────────────────

    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void sendReloginReminders() {
        List<UserConfig> configs = userConfigRepository.findAll();
        log.info("[NOTIFY] re-login reminder START — {} user(s) to check", configs.size());
        int reminded = 0;

        for (UserConfig config : configs) {
            if (!Boolean.TRUE.equals(config.getZerodhaConnected())
                    || config.getZerodhaAccessToken() == null) {
                notificationService.notifyUser(config.getUser().getId(),
                        "Good morning! Please re-connect your Zerodha account to activate today's "
                        + "trading session. Open the app → Settings → Connect Zerodha.");
                reminded++;
            }
        }

        log.info("[NOTIFY] re-login reminder DONE — reminded {} user(s)", reminded);
    }

    // ── 3:45 PM IST — Daily P&L summary ─────────────────────────────────────

    @Scheduled(cron = "0 45 15 * * MON-FRI", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void sendDailySummary() {
        LocalDateTime startOfDay = LocalDate.now(IST).atStartOfDay();

        List<UserConfig> configs = userConfigRepository.findAll();
        log.info("[NOTIFY] daily P&L summary START — {} user(s)", configs.size());
        for (UserConfig config : configs) {
            try {
                buildAndSendSummary(config, startOfDay);
            } catch (Exception e) {
                log.warn("[NOTIFY] daily summary failed for userId={}: {}", config.getUser().getId(), e.getMessage());
            }
        }
        log.info("[NOTIFY] daily P&L summary DONE");
    }

    private void buildAndSendSummary(UserConfig config, LocalDateTime startOfDay) {
        Long userId = config.getUser().getId();

        long active  = positionRepository.countByUserIdAndStatusIn(userId, List.of(PositionStatus.ACTIVE));
        long pending = positionRepository.countByUserIdAndStatusIn(userId, List.of(PositionStatus.PENDING_ENTRY));

        List<Position> closedToday = positionRepository
                .findByUserIdAndStatusInAndClosedAtAfter(userId, CLOSED_STATUSES, startOfDay);

        BigDecimal todayPnl = closedToday.stream()
                .map(p -> p.getRealisedPnl() != null ? p.getRealisedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int wins   = (int) closedToday.stream().filter(p -> p.getStatus() == PositionStatus.CLOSED_TARGET).count();
        int losses = (int) closedToday.stream().filter(p -> p.getStatus() == PositionStatus.CLOSED_SL).count();

        StringBuilder msg = new StringBuilder();
        msg.append("Daily Summary\n");
        msg.append("─────────────────\n");
        msg.append("Active positions: ").append(active).append("\n");
        msg.append("Pending entry:    ").append(pending).append("\n");
        msg.append("\n");

        if (closedToday.isEmpty()) {
            msg.append("No trades closed today.");
        } else {
            msg.append("Closed today: ").append(closedToday.size())
               .append(" trade(s)  ").append(wins).append("W / ").append(losses).append("L\n");
            String sign = todayPnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            msg.append("Today P&L: ").append(sign)
               .append(todayPnl.setScale(2, RoundingMode.HALF_UP).toPlainString());
            msg.append("\n\nTrades closed today:");
            for (Position p : closedToday) {
                String outcome = switch (p.getStatus()) {
                    case CLOSED_TARGET -> "Target";
                    case CLOSED_SL     -> "SL hit";
                    default            -> "Manual";
                };
                String pnlStr = p.getRealisedPnl() != null
                        ? (p.getRealisedPnl().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                          + p.getRealisedPnl().setScale(2, RoundingMode.HALF_UP).toPlainString()
                        : "—";
                msg.append("\n  ").append(p.getSymbol())
                   .append("  ").append(outcome)
                   .append("  ₹").append(pnlStr);
            }
        }

        notificationService.notifyUser(userId, msg.toString());
    }
}
