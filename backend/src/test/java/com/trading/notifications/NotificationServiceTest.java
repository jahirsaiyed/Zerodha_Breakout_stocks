package com.trading.notifications;

import com.trading.common.EncryptionUtil;
import com.trading.signals.Position;
import com.trading.signals.PositionRepository;
import com.trading.users.User;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private TelegramApiClient telegramClient;
    @Mock private PositionRepository positionRepository;
    @Mock private UserConfigRepository userConfigRepository;
    @Mock private EncryptionUtil encryptionUtil;

    @InjectMocks
    private NotificationService notificationService;

    private static final String ENC_TOKEN = "encrypted-token";
    private static final String BOT_TOKEN = "bot-token";
    private static final String CHAT_ID   = "12345";

    private User user;
    private UserConfig configWithBotAndChat;
    private UserConfig configNoChatId;
    private UserConfig configNoToken;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").name("Test").passwordHash("x").build();

        configWithBotAndChat = UserConfig.builder()
                .user(user)
                .telegramChatId(CHAT_ID)
                .telegramBotToken(ENC_TOKEN)
                .build();

        configNoChatId = UserConfig.builder()
                .user(user)
                .telegramChatId(null)
                .telegramBotToken(ENC_TOKEN)
                .build();

        configNoToken = UserConfig.builder()
                .user(user)
                .telegramChatId(CHAT_ID)
                .build();

        lenient().when(encryptionUtil.decrypt(ENC_TOKEN)).thenReturn(BOT_TOKEN);
    }

    // ── notifyForPosition ────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyForPosition sends message when user has bot token and chatId")
    void notifyForPosition_withBotAndChatId_sendsTelegram() {
        Position pos = buildPosition(10L, user);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(pos));
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithBotAndChat));
        when(telegramClient.sendMessage(BOT_TOKEN, CHAT_ID, "Hello RELIANCE")).thenReturn(CHAT_ID);

        notificationService.notifyForPosition(10L, "Hello RELIANCE");

        verify(telegramClient).sendMessage(BOT_TOKEN, CHAT_ID, "Hello RELIANCE");
    }

    @Test
    @DisplayName("notifyForPosition skips when user has no chatId")
    void notifyForPosition_noChatId_skipsTelegram() {
        Position pos = buildPosition(10L, user);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(pos));
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configNoChatId));

        notificationService.notifyForPosition(10L, "Hello");

        verify(telegramClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("notifyForPosition skips when position not found")
    void notifyForPosition_positionNotFound_skips() {
        when(positionRepository.findById(99L)).thenReturn(Optional.empty());

        notificationService.notifyForPosition(99L, "Hello");

        verify(telegramClient, never()).sendMessage(any(), any(), any());
    }

    // ── notifyUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyUser sends message when bot token and chatId are set")
    void notifyUser_withBotAndChatId_sendsTelegram() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithBotAndChat));
        when(telegramClient.sendMessage(BOT_TOKEN, CHAT_ID, "Direct message")).thenReturn(CHAT_ID);

        notificationService.notifyUser(1L, "Direct message");

        verify(telegramClient).sendMessage(BOT_TOKEN, CHAT_ID, "Direct message");
    }

    @Test
    @DisplayName("notifyUser skips when chatId is blank")
    void notifyUser_blankChatId_skips() {
        UserConfig blankChat = UserConfig.builder().user(user).telegramChatId("").telegramBotToken(ENC_TOKEN).build();
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(blankChat));

        notificationService.notifyUser(1L, "Direct message");

        verify(telegramClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("notifyUser skips when bot token is not configured")
    void notifyUser_noToken_skips() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configNoToken));

        notificationService.notifyUser(1L, "Direct message");

        verify(telegramClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("notifyUser skips when no user config found")
    void notifyUser_noConfig_skips() {
        when(userConfigRepository.findByUser_Id(99L)).thenReturn(Optional.empty());

        notificationService.notifyUser(99L, "Direct message");

        verify(telegramClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("notifyUser auto-updates stored chatId when Telegram signals supergroup migration")
    void notifyUser_supergroupMigration_updatesStoredChatId() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithBotAndChat));
        when(telegramClient.sendMessage(BOT_TOKEN, CHAT_ID, "Alert")).thenReturn("-1001234567890");

        notificationService.notifyUser(1L, "Alert");

        verify(userConfigRepository).save(configWithBotAndChat);
        assert configWithBotAndChat.getTelegramChatId().equals("-1001234567890");
    }

    @Test
    @DisplayName("notifyUser does not update config when chatId is unchanged")
    void notifyUser_noMigration_doesNotSaveConfig() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithBotAndChat));
        when(telegramClient.sendMessage(BOT_TOKEN, CHAT_ID, "Alert")).thenReturn(CHAT_ID);

        notificationService.notifyUser(1L, "Alert");

        verify(userConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("notifyUser does not update config when delivery fails")
    void notifyUser_deliveryFailed_doesNotSaveConfig() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithBotAndChat));
        when(telegramClient.sendMessage(BOT_TOKEN, CHAT_ID, "Alert")).thenReturn(null);

        notificationService.notifyUser(1L, "Alert");

        verify(userConfigRepository, never()).save(any());
    }

    private Position buildPosition(Long id, User owner) {
        Position p = new Position();
        p.setId(id);
        p.setUser(owner);
        p.setSymbol("RELIANCE");
        p.setQuantity(5);
        return p;
    }
}
