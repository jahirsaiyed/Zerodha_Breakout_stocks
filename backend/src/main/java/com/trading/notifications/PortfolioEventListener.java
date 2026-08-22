package com.trading.notifications;

import com.trading.portfolio.events.*;
import com.trading.signals.PositionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Listens to portfolio domain events and dispatches Telegram notifications.
 *
 * Each handler runs {@link Async asynchronously} so a slow or failed Telegram
 * API call never blocks the portfolio engine scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioEventListener {

    private final NotificationService notifications;

    @Async
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        String msg = "Order placed\n"
                + "Symbol: " + event.symbol() + "\n"
                + "Order: " + event.zerodhaOrderId();
        notifications.notifyForPosition(event.positionId(), msg);
    }

    @Async
    @EventListener
    public void onOrderFilled(OrderFilledEvent event) {
        String msg = "Order filled\n"
                + "Symbol: " + event.symbol() + "\n"
                + "Qty: " + event.filledQty() + "\n"
                + "Avg price: " + format(event.avgPrice())
                + (event.gttId() != null ? "\nGTT placed: " + event.gttId() : "");
        notifications.notifyForPosition(event.positionId(), msg);
    }

    @Async
    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        String msg = "Order cancelled\n"
                + "Symbol: " + event.symbol() + "\n"
                + "Reason: " + event.reason();
        notifications.notifyForPosition(event.positionId(), msg);
    }

    @Async
    @EventListener
    public void onOrderExpired(OrderExpiredEvent event) {
        String msg = "Order expired (no fill)\n"
                + "Symbol: " + event.symbol() + "\n"
                + "Order: " + event.zerodhaOrderId();
        notifications.notifyForPosition(event.positionId(), msg);
    }

    @Async
    @EventListener
    public void onPositionClosed(PositionClosedEvent event) {
        String header = switch (event.closeStatus()) {
            case CLOSED_TARGET -> "Target hit";
            case CLOSED_SL     -> "Stop loss hit";
            case CLOSED_MANUAL -> "Manual exit";
            default            -> "Position closed (" + event.closeStatus() + ")";
        };
        StringBuilder msg = new StringBuilder(header)
                .append("\nSymbol: ").append(event.symbol());
        if (event.realisedPnl() != null) {
            BigDecimal pnl = event.realisedPnl().setScale(2, RoundingMode.HALF_UP);
            msg.append("\nP&L: ").append(pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "").append(pnl);
        }
        notifications.notifyForPosition(event.positionId(), msg.toString());
    }

    @Async
    @EventListener
    public void onUnmanagedPosition(UnmanagedPositionEvent event) {
        String msg = "Alert: unmanaged position detected\n"
                + "Symbol: " + event.symbol() + "\n"
                + "Qty: " + event.quantity() + "\n"
                + "LTP: " + format(event.lastPrice());
        notifications.notifyUser(event.userId(), msg);
    }

    private static String format(BigDecimal value) {
        return value == null ? "N/A" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
