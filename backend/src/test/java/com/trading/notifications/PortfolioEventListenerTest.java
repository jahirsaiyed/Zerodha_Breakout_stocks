package com.trading.notifications;

import com.trading.portfolio.events.*;
import com.trading.signals.PositionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioEventListenerTest {

    @Mock
    private NotificationService notifications;

    @InjectMocks
    private PortfolioEventListener listener;

    @Test
    @DisplayName("onOrderPlaced notifies with symbol and order ID")
    void onOrderPlaced_notifiesForPosition() {
        var event = new OrderPlacedEvent(10L, "RELIANCE", "ORD123");
        listener.onOrderPlaced(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        assertThat(msgCaptor.getValue())
                .contains("RELIANCE")
                .contains("ORD123");
    }

    @Test
    @DisplayName("onOrderFilled notifies with qty and avg price")
    void onOrderFilled_notifiesForPosition() {
        var event = new OrderFilledEvent(10L, "RELIANCE", "ORD123", 5, BigDecimal.valueOf(2410.5), "GTT456");
        listener.onOrderFilled(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        String msg = msgCaptor.getValue();
        assertThat(msg).contains("RELIANCE").contains("5").contains("2410.50").contains("GTT456");
    }

    @Test
    @DisplayName("onOrderFilled includes no GTT line when gttId is null")
    void onOrderFilled_noGtt_omitsGttLine() {
        var event = new OrderFilledEvent(10L, "RELIANCE", "ORD123", 5, BigDecimal.valueOf(2410), null);
        listener.onOrderFilled(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).doesNotContain("GTT");
    }

    @Test
    @DisplayName("onOrderCancelled notifies with symbol and reason")
    void onOrderCancelled_notifiesForPosition() {
        var event = new OrderCancelledEvent(10L, "RELIANCE", "ORD123", "REJECTED");
        listener.onOrderCancelled(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("RELIANCE").contains("REJECTED");
    }

    @Test
    @DisplayName("onOrderExpired notifies with symbol")
    void onOrderExpired_notifiesForPosition() {
        var event = new OrderExpiredEvent(10L, "RELIANCE", "ORD123");
        listener.onOrderExpired(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("RELIANCE").contains("ORD123");
    }

    @Test
    @DisplayName("onPositionClosed CLOSED_TARGET shows target hit with P&L")
    void onPositionClosed_targetHit_showsTargetHeader() {
        var event = new PositionClosedEvent(10L, "RELIANCE", PositionStatus.CLOSED_TARGET, BigDecimal.valueOf(2500));
        listener.onPositionClosed(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        String msg = msgCaptor.getValue();
        assertThat(msg).contains("Target hit").contains("RELIANCE").contains("+2500.00");
    }

    @Test
    @DisplayName("onPositionClosed CLOSED_SL shows stop loss hit with negative P&L")
    void onPositionClosed_slHit_showsSlHeader() {
        var event = new PositionClosedEvent(10L, "RELIANCE", PositionStatus.CLOSED_SL, BigDecimal.valueOf(-800));
        listener.onPositionClosed(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        String msg = msgCaptor.getValue();
        assertThat(msg).contains("Stop loss hit").contains("-800.00");
    }

    @Test
    @DisplayName("onPositionClosed CLOSED_MANUAL shows manual exit")
    void onPositionClosed_manual_showsManualHeader() {
        var event = new PositionClosedEvent(10L, "RELIANCE", PositionStatus.CLOSED_MANUAL, null);
        listener.onPositionClosed(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyForPosition(eq(10L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Manual exit").contains("RELIANCE");
    }

    @Test
    @DisplayName("onUnmanagedPosition notifies user directly with symbol and qty")
    void onUnmanagedPosition_notifiesUser() {
        var event = new UnmanagedPositionEvent(1L, "HDFC", 10, BigDecimal.valueOf(1800.50));
        listener.onUnmanagedPosition(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyUser(eq(1L), msgCaptor.capture());
        String msg = msgCaptor.getValue();
        assertThat(msg).contains("HDFC").contains("10").contains("1800.50");
    }
}
