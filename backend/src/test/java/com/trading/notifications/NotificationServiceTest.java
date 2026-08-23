package com.trading.notifications;

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

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private UserConfig configWithChatId;
    private UserConfig configNoChatId;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").name("Test").passwordHash("x").build();

        configWithChatId = UserConfig.builder()
                .user(user)
                .telegramChatId("12345")
                .build();

        configNoChatId = UserConfig.builder()
                .user(user)
                .telegramChatId(null)
                .build();
    }

    // ── notifyForPosition ────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyForPosition sends message when user has chatId")
    void notifyForPosition_withChatId_sendsTelegram() {
        Position pos = buildPosition(10L, user);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(pos));
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithChatId));
        when(telegramClient.sendMessage("12345", "Hello RELIANCE")).thenReturn("12345");

        notificationService.notifyForPosition(10L, "Hello RELIANCE");

        verify(telegramClient).sendMessage("12345", "Hello RELIANCE");
    }

    @Test
    @DisplayName("notifyForPosition skips when user has no chatId")
    void notifyForPosition_noChatId_skipsTelegram() {
        Position pos = buildPosition(10L, user);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(pos));
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configNoChatId));

        notificationService.notifyForPosition(10L, "Hello");

        verify(telegramClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyForPosition skips when position not found")
    void notifyForPosition_positionNotFound_skips() {
        when(positionRepository.findById(99L)).thenReturn(Optional.empty());

        notificationService.notifyForPosition(99L, "Hello");

        verify(telegramClient, never()).sendMessage(anyString(), anyString());
    }

    // ── notifyUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyUser sends message when chatId is set")
    void notifyUser_withChatId_sendsTelegram() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithChatId));
        when(telegramClient.sendMessage("12345", "Direct message")).thenReturn("12345");

        notificationService.notifyUser(1L, "Direct message");

        verify(telegramClient).sendMessage("12345", "Direct message");
    }

    @Test
    @DisplayName("notifyUser skips when chatId is blank")
    void notifyUser_blankChatId_skips() {
        UserConfig blankChat = UserConfig.builder().user(user).telegramChatId("").build();
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(blankChat));

        notificationService.notifyUser(1L, "Direct message");

        verify(telegramClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyUser skips when no user config found")
    void notifyUser_noConfig_skips() {
        when(userConfigRepository.findByUser_Id(99L)).thenReturn(Optional.empty());

        notificationService.notifyUser(99L, "Direct message");

        verify(telegramClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyUser auto-updates stored chatId when Telegram signals supergroup migration")
    void notifyUser_supergroupMigration_updatesStoredChatId() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithChatId));
        // Telegram returns the new supergroup ID after migration
        when(telegramClient.sendMessage("12345", "Alert")).thenReturn("-1001234567890");

        notificationService.notifyUser(1L, "Alert");

        verify(userConfigRepository).save(configWithChatId);
        // Verify the config was updated with the new ID
        assert configWithChatId.getTelegramChatId().equals("-1001234567890");
    }

    @Test
    @DisplayName("notifyUser does not update config when chatId is unchanged")
    void notifyUser_noMigration_doesNotSaveConfig() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithChatId));
        when(telegramClient.sendMessage("12345", "Alert")).thenReturn("12345");

        notificationService.notifyUser(1L, "Alert");

        verify(userConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("notifyUser does not update config when delivery fails")
    void notifyUser_deliveryFailed_doesNotSaveConfig() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithChatId));
        when(telegramClient.sendMessage("12345", "Alert")).thenReturn(null);

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
