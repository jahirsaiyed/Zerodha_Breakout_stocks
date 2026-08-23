package com.trading.notifications;

import com.trading.signals.Position;
import com.trading.signals.PositionRepository;
import com.trading.signals.PositionStatus;
import com.trading.signals.Signal;
import com.trading.signals.SignalRepository;
import com.trading.signals.SignalStatus;
import com.trading.users.User;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramBotServiceTest {

    @Mock TelegramProperties props;
    @Mock TelegramApiClient telegramClient;
    @Mock UserConfigRepository userConfigRepository;
    @Mock PositionRepository positionRepository;
    @Mock SignalRepository signalRepository;
    @Mock RestTemplate restTemplate;

    TelegramBotService botService;

    private static final String CHAT_ID = "123456789";

    @BeforeEach
    void setUp() {
        lenient().when(props.isEnabled()).thenReturn(true);
        lenient().when(props.getBotToken()).thenReturn("bot-token");
        lenient().when(props.getBaseUrl()).thenReturn("https://api.telegram.org");

        botService = new TelegramBotService(props, telegramClient, userConfigRepository,
                positionRepository, signalRepository);
        ReflectionTestUtils.setField(botService, "restTemplate", restTemplate);
    }

    private void mockTelegramUpdate(String command) {
        String json = """
                {"ok":true,"result":[{"update_id":1,"message":{"text":"%s","chat":{"id":%s,"type":"private"}}}]}
                """.formatted(command, CHAT_ID);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));
    }

    private UserConfig configForChatId(String chatId) {
        User user = User.builder().id(1L).build();
        return UserConfig.builder().user(user).telegramChatId(chatId).zerodhaConnected(false).build();
    }

    @Test
    @DisplayName("/portfolio with no positions sends empty message")
    void portfolioCommand_noPositions_sendsEmptyMessage() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configForChatId(CHAT_ID)));
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.ACTIVE)).thenReturn(List.of());
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.PENDING_ENTRY)).thenReturn(List.of());
        mockTelegramUpdate("/portfolio");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(CHAT_ID), contains("No open positions"));
    }

    @Test
    @DisplayName("/portfolio with active positions lists them")
    void portfolioCommand_withActivePositions_listsSymbols() {
        Position pos = new Position();
        pos.setSymbol("RELIANCE");
        pos.setQuantity(5);
        pos.setAvgEntryPrice(new BigDecimal("2400.00"));
        pos.setStatus(PositionStatus.ACTIVE);

        when(userConfigRepository.findAll()).thenReturn(List.of(configForChatId(CHAT_ID)));
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.ACTIVE)).thenReturn(List.of(pos));
        when(positionRepository.findByUserIdAndStatus(1L, PositionStatus.PENDING_ENTRY)).thenReturn(List.of());
        mockTelegramUpdate("/portfolio");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(CHAT_ID), contains("RELIANCE"));
    }

    @Test
    @DisplayName("/signals with no active signals sends empty message")
    void signalsCommand_noActiveSignals_sendsEmptyMessage() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configForChatId(CHAT_ID)));
        when(signalRepository.findByStatus(SignalStatus.ACTIVE)).thenReturn(List.of());
        mockTelegramUpdate("/signals");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(CHAT_ID), contains("No active signals"));
    }

    @Test
    @DisplayName("/summary sends P&L summary with win/loss counts")
    void summaryCommand_sendsPnlSummary() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configForChatId(CHAT_ID)));
        when(positionRepository.findByUserIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of());
        mockTelegramUpdate("/summary");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(CHAT_ID), contains("Summary"));
    }

    @Test
    @DisplayName("/status sends system status message")
    void statusCommand_sendsSystemStatus() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configForChatId(CHAT_ID)));
        mockTelegramUpdate("/status");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(CHAT_ID), contains("System Status"));
    }

    @Test
    @DisplayName("unknown command sends help message")
    void unknownCommand_sendsHelpMessage() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configForChatId(CHAT_ID)));
        mockTelegramUpdate("/unknown");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(CHAT_ID), contains("Unknown command"));
    }

    @Test
    @DisplayName("message from unknown chat ID is silently ignored")
    void unknownChatId_silentlyIgnored() {
        when(userConfigRepository.findAll()).thenReturn(List.of()); // no matching config
        mockTelegramUpdate("/portfolio");

        botService.pollUpdates();

        verify(telegramClient, never()).sendMessage(any(), any());
    }

    // ── Chat discovery ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDiscoveredChats returns empty list before any updates are polled")
    void getDiscoveredChats_beforeAnyPolls_returnsEmpty() {
        assertThat(botService.getDiscoveredChats()).isEmpty();
    }

    @Test
    @DisplayName("pollUpdates records group chat metadata in discoveredChats")
    void pollUpdates_groupChatUpdate_recordsChatInfo() {
        String json = """
                {"ok":true,"result":[{"update_id":10,"message":{
                  "text":"/status",
                  "chat":{"id":999,"type":"group","title":"Trading Alerts"}
                }}]}
                """;
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));
        when(userConfigRepository.findAll()).thenReturn(List.of());

        botService.pollUpdates();

        List<TelegramChatDto> chats = botService.getDiscoveredChats();
        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).chatId()).isEqualTo("999");
        assertThat(chats.get(0).chatTitle()).isEqualTo("Trading Alerts");
        assertThat(chats.get(0).chatType()).isEqualTo("group");
    }

    @Test
    @DisplayName("pollUpdates records private chat using first_name as title")
    void pollUpdates_privateChatUpdate_usesFirstNameAsTitle() {
        String json = """
                {"ok":true,"result":[{"update_id":11,"message":{
                  "text":"/status",
                  "chat":{"id":777,"type":"private","first_name":"Alice","last_name":"Smith"}
                }}]}
                """;
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));
        when(userConfigRepository.findAll()).thenReturn(List.of());

        botService.pollUpdates();

        List<TelegramChatDto> chats = botService.getDiscoveredChats();
        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).chatId()).isEqualTo("777");
        assertThat(chats.get(0).chatTitle()).isEqualTo("Alice Smith");
        assertThat(chats.get(0).chatType()).isEqualTo("private");
    }

    @Test
    @DisplayName("pollUpdates records channel_post chat for channels")
    void pollUpdates_channelPost_recordsChannelInfo() {
        String json = """
                {"ok":true,"result":[{"update_id":12,"channel_post":{
                  "text":"hello",
                  "chat":{"id":-100123,"type":"channel","title":"My Channel"}
                }}]}
                """;
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        botService.pollUpdates();

        List<TelegramChatDto> chats = botService.getDiscoveredChats();
        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).chatId()).isEqualTo("-100123");
        assertThat(chats.get(0).chatTitle()).isEqualTo("My Channel");
        assertThat(chats.get(0).chatType()).isEqualTo("channel");
    }

    @Test
    @DisplayName("discoveredChats deduplicates repeated updates from same chat")
    void pollUpdates_repeatedSameChat_deduplicates() {
        String json = """
                {"ok":true,"result":[
                  {"update_id":20,"message":{"text":"/status","chat":{"id":555,"type":"private","first_name":"Bob"}}},
                  {"update_id":21,"message":{"text":"/status","chat":{"id":555,"type":"private","first_name":"Bob"}}}
                ]}
                """;
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));
        when(userConfigRepository.findAll()).thenReturn(List.of());

        botService.pollUpdates();

        assertThat(botService.getDiscoveredChats()).hasSize(1);
    }
}
