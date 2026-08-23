package com.trading.notifications;

import com.trading.common.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramBotConfigServiceTest {

    @Mock TelegramBotConfigRepository repository;
    @Mock EncryptionUtil encryptionUtil;
    @Mock RestTemplate restTemplate;

    TelegramBotConfigService service;

    private TelegramProperties buildProps() {
        TelegramProperties p = new TelegramProperties();
        p.setBaseUrl("https://api.telegram.org");
        return p;
    }

    @BeforeEach
    void setUp() {
        service = new TelegramBotConfigService(repository, buildProps(), encryptionUtil);
    }

    // ── getConfig ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getConfig returns empty dto when no row exists")
    void getConfig_noRow_returnsEmptyDto() {
        when(repository.findFirstBy()).thenReturn(Optional.empty());

        TelegramBotConfigDto dto = service.getConfig();

        assertThat(dto.hasToken()).isFalse();
        assertThat(dto.enabled()).isFalse();
        assertThat(dto.botName()).isNull();
        assertThat(dto.botUsername()).isNull();
    }

    @Test
    @DisplayName("getConfig returns dto with token flag set, token value never exposed")
    void getConfig_configuredBot_returnsDtoWithoutToken() {
        TelegramBotConfig config = TelegramBotConfig.builder()
                .botToken("encrypted-token")
                .botName("My Bot")
                .botUsername("mybot")
                .enabled(true)
                .build();
        when(repository.findFirstBy()).thenReturn(Optional.of(config));

        TelegramBotConfigDto dto = service.getConfig();

        assertThat(dto.hasToken()).isTrue();
        assertThat(dto.botName()).isEqualTo("My Bot");
        assertThat(dto.botUsername()).isEqualTo("mybot");
        assertThat(dto.enabled()).isTrue();
    }

    // ── getActiveToken ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveToken returns empty when bot is disabled")
    void getActiveToken_disabled_returnsEmpty() {
        TelegramBotConfig config = TelegramBotConfig.builder()
                .botToken("encrypted")
                .enabled(false)
                .build();
        when(repository.findFirstBy()).thenReturn(Optional.of(config));

        assertThat(service.getActiveToken()).isEmpty();
    }

    @Test
    @DisplayName("getActiveToken returns empty when no token is stored")
    void getActiveToken_noToken_returnsEmpty() {
        TelegramBotConfig config = TelegramBotConfig.builder()
                .botToken(null)
                .enabled(true)
                .build();
        when(repository.findFirstBy()).thenReturn(Optional.of(config));

        assertThat(service.getActiveToken()).isEmpty();
    }

    @Test
    @DisplayName("getActiveToken decrypts and returns token when enabled and token is present")
    void getActiveToken_enabledWithToken_returnsDecrypted() {
        TelegramBotConfig config = TelegramBotConfig.builder()
                .botToken("encrypted-value")
                .enabled(true)
                .build();
        when(repository.findFirstBy()).thenReturn(Optional.of(config));
        when(encryptionUtil.decrypt("encrypted-value")).thenReturn("real-bot-token");

        assertThat(service.getActiveToken()).contains("real-bot-token");
    }

    // ── connectBot ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connectBot throws when token is blank")
    void connectBot_blankToken_throws() {
        assertThatThrownBy(() -> service.connectBot("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("connectBot throws when Telegram rejects the token")
    void connectBot_invalidToken_throws() {
        TelegramBotConfigService spyService = spy(service);
        doThrow(new IllegalArgumentException("Telegram rejected the token: Unauthorized"))
                .when(spyService).connectBot("bad-token");

        assertThatThrownBy(() -> spyService.connectBot("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Telegram rejected");
    }

    // ── disconnectBot ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnectBot clears token, name, username and sets enabled=false")
    void disconnectBot_clearsConfig() {
        TelegramBotConfig config = TelegramBotConfig.builder()
                .botToken("encrypted")
                .botName("My Bot")
                .botUsername("mybot")
                .enabled(true)
                .build();
        when(repository.findFirstBy()).thenReturn(Optional.of(config));

        service.disconnectBot();

        ArgumentCaptor<TelegramBotConfig> captor = ArgumentCaptor.forClass(TelegramBotConfig.class);
        verify(repository).save(captor.capture());
        TelegramBotConfig saved = captor.getValue();
        assertThat(saved.getBotToken()).isNull();
        assertThat(saved.getBotName()).isNull();
        assertThat(saved.getBotUsername()).isNull();
        assertThat(saved.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("disconnectBot is a no-op when no config row exists")
    void disconnectBot_noRow_doesNothing() {
        when(repository.findFirstBy()).thenReturn(Optional.empty());

        service.disconnectBot();

        verify(repository, never()).save(any());
    }
}
