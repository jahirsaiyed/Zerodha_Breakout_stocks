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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailySchedulerTest {

    @Mock UserConfigRepository userConfigRepository;
    @Mock PositionRepository positionRepository;
    @Mock NotificationService notificationService;
    @Mock LivePriceService livePriceService;

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
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.ACTIVE))
                .thenReturn(List.of(activePosition("TCS", 1, "100.00"), activePosition("INFY", 1, "100.00")));
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

    // ── sendDailySummary — open positions section ───────────────────────────────

    @Test
    @DisplayName("sendDailySummary lists open positions with live LTP, unrealised P&L, and a total")
    void sendDailySummary_withActivePositions_listsOpenPositionsAndTotal() {
        Position aaa = activePosition("AAA", 10, "100.00");
        Position bbb = activePosition("BBB", 5, "200.00");

        when(userConfigRepository.findAll()).thenReturn(List.of(config));
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.ACTIVE))
                .thenReturn(List.of(aaa, bbb));
        when(positionRepository.countByUserIdAndStatusIn(1L, List.of(PositionStatus.PENDING_ENTRY))).thenReturn(0L);
        when(positionRepository.findByUserIdAndStatusInAndClosedAtAfter(eq(1L), anyList(), any()))
                .thenReturn(List.of());
        when(livePriceService.getLivePrices(eq(config), anySet())).thenReturn(Map.of(
                "AAA", new BigDecimal("110.00"),
                "BBB", new BigDecimal("190.00")));

        scheduler.sendDailySummary();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(1L), msgCaptor.capture());
        String msg = msgCaptor.getValue();

        assertThat(msg).contains("Open Positions:");
        assertThat(msg).contains("AAA").contains("ltp=110.00").contains("P&L=+100.00");
        assertThat(msg).contains("BBB").contains("ltp=190.00").contains("P&L=-50.00");
        assertThat(msg).contains("Total unrealised P&L: +50.00");
    }

    @Test
    @DisplayName("sendDailySummary shows '—' and an unavailable-price note when a position's LTP can't be fetched")
    void sendDailySummary_missingLtpForSomePositions_showsUnavailableNote() {
        Position aaa = activePosition("AAA", 10, "100.00");
        Position bbb = activePosition("BBB", 5, "200.00");

        when(userConfigRepository.findAll()).thenReturn(List.of(config));
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.ACTIVE))
                .thenReturn(List.of(aaa, bbb));
        when(positionRepository.countByUserIdAndStatusIn(1L, List.of(PositionStatus.PENDING_ENTRY))).thenReturn(0L);
        when(positionRepository.findByUserIdAndStatusInAndClosedAtAfter(eq(1L), anyList(), any()))
                .thenReturn(List.of());
        when(livePriceService.getLivePrices(eq(config), anySet()))
                .thenReturn(Map.of("AAA", new BigDecimal("110.00")));

        scheduler.sendDailySummary();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(1L), msgCaptor.capture());
        String msg = msgCaptor.getValue();

        assertThat(msg).contains("BBB").contains("ltp=—").contains("P&L=—");
        assertThat(msg).contains("Total unrealised P&L: +100.00");
        assertThat(msg).contains("(1 position(s) priced unavailable)");
    }

    @Test
    @DisplayName("sendDailySummary caps the listed open positions and shows a trailer for the rest")
    void sendDailySummary_moreActivePositionsThanCap_showsTrailer() {
        List<Position> manyActive = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            manyActive.add(activePosition("SYM" + i, 1, "100.00"));
        }

        when(userConfigRepository.findAll()).thenReturn(List.of(config));
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.ACTIVE)).thenReturn(manyActive);
        when(positionRepository.countByUserIdAndStatusIn(1L, List.of(PositionStatus.PENDING_ENTRY))).thenReturn(0L);
        when(positionRepository.findByUserIdAndStatusInAndClosedAtAfter(eq(1L), anyList(), any()))
                .thenReturn(List.of());
        when(livePriceService.getLivePrices(eq(config), anySet())).thenReturn(Map.of());

        scheduler.sendDailySummary();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(1L), msgCaptor.capture());
        String msg = msgCaptor.getValue();

        assertThat(msg).contains("Active positions: 26");
        assertThat(msg).contains("...and 1 more");
        assertThat(msg).contains("(26 position(s) priced unavailable)");
    }

    private Position activePosition(String symbol, int quantity, String avgEntryPrice) {
        Position p = new Position();
        p.setSymbol(symbol);
        p.setQuantity(quantity);
        p.setAvgEntryPrice(new BigDecimal(avgEntryPrice));
        p.setStatus(PositionStatus.ACTIVE);
        return p;
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
