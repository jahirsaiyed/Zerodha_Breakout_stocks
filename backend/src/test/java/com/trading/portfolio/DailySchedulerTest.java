package com.trading.portfolio;

import com.trading.notifications.NotificationService;
import com.trading.signals.Position;
import com.trading.signals.PositionRepository;
import com.trading.signals.PositionStatus;
import com.trading.users.User;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailySchedulerTest {

    @Mock UserConfigRepository userConfigRepository;
    @Mock PositionRepository positionRepository;
    @Mock NotificationService notificationService;

    @InjectMocks DailyScheduler scheduler;

    private UserConfig config;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("alice@test.com").name("Alice").passwordHash("x").build();
        config = UserConfig.builder().user(user).build();
    }

    // ── sendReloginReminders ──────────────────────────────────────────────────

    @Test
    @DisplayName("sendReloginReminders notifies users with missing Zerodha token")
    void sendReloginReminders_disconnectedUser_sendsNotification() {
        config.setZerodhaConnected(false);
        config.setZerodhaAccessToken(null);
        config.setTelegramBotToken("bot123:token");
        config.setTelegramChatId("456");
        when(userConfigRepository.findAll()).thenReturn(List.of(config));

        scheduler.sendReloginReminders();

        verify(notificationService).notifyUser(eq(1L), contains("re-connect"));
    }

    @Test
    @DisplayName("sendReloginReminders skips connected users with valid token")
    void sendReloginReminders_connectedUser_skips() {
        config.setZerodhaConnected(true);
        config.setZerodhaAccessToken("validToken");
        when(userConfigRepository.findAll()).thenReturn(List.of(config));

        scheduler.sendReloginReminders();

        verify(notificationService, never()).notifyUser(anyLong(), anyString());
    }

    // ── sendDailySummary ──────────────────────────────────────────────────────

    @Test
    @DisplayName("sendDailySummary sends summary message with trade details")
    void sendDailySummary_withClosedTrades_sendsSummary() {
        when(userConfigRepository.findAll()).thenReturn(List.of(config));
        when(positionRepository.countByUserIdAndStatusIn(1L, List.of(PositionStatus.ACTIVE))).thenReturn(2L);
        when(positionRepository.countByUserIdAndStatusIn(1L, List.of(PositionStatus.PENDING_ENTRY))).thenReturn(1L);

        Position closed = new Position();
        closed.setSymbol("RELIANCE");
        closed.setStatus(PositionStatus.CLOSED_TARGET);
        closed.setRealisedPnl(BigDecimal.valueOf(500));
        closed.setClosedAt(LocalDateTime.now());

        when(positionRepository.findByUserIdAndStatusInAndClosedAtAfter(eq(1L), anyList(), any()))
                .thenReturn(List.of(closed));

        scheduler.sendDailySummary();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(1L), msgCaptor.capture());

        String msg = msgCaptor.getValue();
        assertThat(msg).contains("Daily Summary");
        assertThat(msg).contains("Active positions: 2");
        assertThat(msg).contains("RELIANCE");
        assertThat(msg).contains("Target");
        assertThat(msg).contains("+500");
    }

    @Test
    @DisplayName("sendDailySummary sends summary with no-trades message when nothing closed today")
    void sendDailySummary_noClosedTrades_sendsNoTradesMessage() {
        when(userConfigRepository.findAll()).thenReturn(List.of(config));
        when(positionRepository.countByUserIdAndStatusIn(anyLong(), anyList())).thenReturn(0L);
        when(positionRepository.findByUserIdAndStatusInAndClosedAtAfter(anyLong(), anyList(), any()))
                .thenReturn(List.of());

        scheduler.sendDailySummary();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(1L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("No trades closed today");
    }

    @Test
    @DisplayName("sendDailySummary continues for other users when one fails")
    void sendDailySummary_exceptionForOneUser_continuesForOthers() {
        User user2 = User.builder().id(2L).email("bob@test.com").name("Bob").passwordHash("x").build();
        UserConfig config2 = UserConfig.builder().user(user2).build();

        when(userConfigRepository.findAll()).thenReturn(List.of(config, config2));

        // User 1 throws
        when(positionRepository.countByUserIdAndStatusIn(eq(1L), anyList()))
                .thenThrow(new RuntimeException("DB error"));
        // User 2 succeeds
        when(positionRepository.countByUserIdAndStatusIn(eq(2L), anyList())).thenReturn(0L);
        when(positionRepository.findByUserIdAndStatusInAndClosedAtAfter(eq(2L), anyList(), any()))
                .thenReturn(List.of());

        scheduler.sendDailySummary();

        // Only user 2 gets notified (user 1 threw)
        verify(notificationService, times(1)).notifyUser(eq(2L), anyString());
    }
}
