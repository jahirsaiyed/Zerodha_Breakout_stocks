package com.trading.notifications;

import com.trading.common.EncryptionUtil;
import com.trading.signals.Position;
import com.trading.signals.PositionRepository;
import com.trading.signals.PositionStatus;
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
    @Mock EncryptionUtil encryptionUtil;
    @Mock RestTemplate restTemplate;

    TelegramBotService botService;

    private static final long   USER_ID   = 1L;
    private static final String CHAT_ID   = "123456789";
    private static final String ENC_TOKEN = "encrypted-bot-token";
    private static final String BOT_TOKEN = "bot-token";

    @BeforeEach
    void setUp() {
        lenient().when(props.getBaseUrl()).thenReturn("https://api.telegram.org");
        lenient().when(encryptionUtil.decrypt(ENC_TOKEN)).thenReturn(BOT_TOKEN);

        botService = new TelegramBotService(props, telegramClient, userConfigRepository,
                positionRepository, signalRepository, encryptionUtil);
        ReflectionTestUtils.setField(botService, "restTemplate", restTemplate);
    }

    private UserConfig configWithBot(String chatId) {
        User user = User.builder().id(USER_ID).build();
        return UserConfig.builder()
                .user(user)
                .telegramChatId(chatId)
                .telegramBotToken(ENC_TOKEN)
                .zerodhaConnected(false)
                .build();
    }

    private void mockBotUpdate(String command) {
        String json = """
                {"ok":true,"result":[{"update_id":1,"message":{"text":"%s","chat":{"id":%s,"type":"private"}}}]}
                """.formatted(command, CHAT_ID);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));
    }

    // ── Command handling ──────────────────────────────────────────────────────

    @Test
    @DisplayName("/portfolio with no positions sends empty message")
    void portfolioCommand_noPositions_sendsEmptyMessage() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));
        when(positionRepository.findByUserIdAndStatus(USER_ID, PositionStatus.ACTIVE)).thenReturn(List.of());
        when(positionRepository.findByUserIdAndStatus(USER_ID, PositionStatus.PENDING_ENTRY)).thenReturn(List.of());
        mockBotUpdate("/portfolio");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(BOT_TOKEN), eq(CHAT_ID), contains("No open positions"));
    }

    @Test
    @DisplayName("/portfolio with active positions lists them")
    void portfolioCommand_withActivePositions_listsSymbols() {
        Position pos = new Position();
        pos.setSymbol("RELIANCE");
        pos.setQuantity(5);
        pos.setAvgEntryPrice(new BigDecimal("2400.00"));
        pos.setStatus(PositionStatus.ACTIVE);

        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));
        when(positionRepository.findByUserIdAndStatus(USER_ID, PositionStatus.ACTIVE)).thenReturn(List.of(pos));
        when(positionRepository.findByUserIdAndStatus(USER_ID, PositionStatus.PENDING_ENTRY)).thenReturn(List.of());
        mockBotUpdate("/portfolio");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(BOT_TOKEN), eq(CHAT_ID), contains("RELIANCE"));
    }

    @Test
    @DisplayName("/signals with no active signals sends empty message")
    void signalsCommand_noActiveSignals_sendsEmptyMessage() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));
        when(signalRepository.findByStatus(SignalStatus.ACTIVE)).thenReturn(List.of());
        mockBotUpdate("/signals");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(BOT_TOKEN), eq(CHAT_ID), contains("No active signals"));
    }

    @Test
    @DisplayName("/summary sends P&L summary with win/loss counts")
    void summaryCommand_sendsPnlSummary() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));
        when(positionRepository.findByUserIdAndStatusIn(eq(USER_ID), anyList())).thenReturn(List.of());
        mockBotUpdate("/summary");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(BOT_TOKEN), eq(CHAT_ID), contains("Summary"));
    }

    @Test
    @DisplayName("/status sends system status message")
    void statusCommand_sendsSystemStatus() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));
        mockBotUpdate("/status");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(BOT_TOKEN), eq(CHAT_ID), contains("System Status"));
    }

    @Test
    @DisplayName("unknown command sends help message")
    void unknownCommand_sendsHelpMessage() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));
        mockBotUpdate("/unknown");

        botService.pollUpdates();

        verify(telegramClient).sendMessage(eq(BOT_TOKEN), eq(CHAT_ID), contains("Unknown command"));
    }

    @Test
    @DisplayName("command from chatId that does not match user config is silently ignored")
    void unknownChatId_silentlyIgnored() {
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot("999999")));
        mockBotUpdate("/portfolio");

        botService.pollUpdates();

        verify(telegramClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("users without a bot token are skipped during polling")
    void pollUpdates_noToken_skipped() {
        User user = User.builder().id(USER_ID).build();
        UserConfig noToken = UserConfig.builder().user(user).telegramChatId(CHAT_ID).build();
        when(userConfigRepository.findAll()).thenReturn(List.of(noToken));

        botService.pollUpdates();

        verifyNoInteractions(restTemplate);
    }

    // ── Chat discovery ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDiscoveredChatsForUser returns empty list before any updates are polled")
    void getDiscoveredChatsForUser_beforeAnyPolls_returnsEmpty() {
        assertThat(botService.getDiscoveredChatsForUser(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("pollUpdates records group chat metadata scoped to the user")
    void pollUpdates_groupChatUpdate_recordsChatInfo() {
        String json = """
                {"ok":true,"result":[{"update_id":10,"message":{
                  "text":"/status",
                  "chat":{"id":999,"type":"group","title":"Trading Alerts"}
                }}]}
                """;
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok(json));
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));

        botService.pollUpdates();

        List<TelegramChatDto> chats = botService.getDiscoveredChatsForUser(USER_ID);
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
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok(json));
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));

        botService.pollUpdates();

        List<TelegramChatDto> chats = botService.getDiscoveredChatsForUser(USER_ID);
        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).chatId()).isEqualTo("777");
        assertThat(chats.get(0).chatTitle()).isEqualTo("Alice Smith");
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
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok(json));
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));

        botService.pollUpdates();

        List<TelegramChatDto> chats = botService.getDiscoveredChatsForUser(USER_ID);
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
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok(json));
        when(userConfigRepository.findAll()).thenReturn(List.of(configWithBot(CHAT_ID)));

        botService.pollUpdates();

        assertThat(botService.getDiscoveredChatsForUser(USER_ID)).hasSize(1);
    }
}
