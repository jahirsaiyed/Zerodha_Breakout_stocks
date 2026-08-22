package com.trading.zerodha;

import com.trading.broker.BrokerAdapterFactory;
import com.trading.broker.ZerodhaProperties;
import com.trading.common.EncryptionUtil;
import com.trading.users.User;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZerodhaAuthServiceTest {

    @Mock ZerodhaProperties props;
    @Mock BrokerAdapterFactory brokerAdapterFactory;
    @Mock EncryptionUtil encryptionUtil;
    @Mock UserConfigRepository userConfigRepository;
    @InjectMocks ZerodhaAuthService zerodhaAuthService;

    private UserConfig configWithKey() {
        User user = User.builder().id(1L).build();
        return UserConfig.builder().user(user).zerodhaApiKey("apiKey123").zerodhaConnected(false).build();
    }

    @Test
    @DisplayName("initiate: no API key configured → throws")
    void initiate_noApiKey_throws() {
        User user = User.builder().id(1L).build();
        UserConfig config = UserConfig.builder().user(user).zerodhaApiKey(null).zerodhaConnected(false).build();
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> zerodhaAuthService.initiate(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");
    }

    @Test
    @DisplayName("initiate: valid API key → returns nonce and Zerodha login URL")
    void initiate_withApiKey_returnsNonceAndUrl() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithKey()));
        when(props.getLoginBaseUrl()).thenReturn("https://kite.zerodha.com/connect/login?v=3&api_key=");

        ZerodhaAuthService.OAuthInitResult result = zerodhaAuthService.initiate(1L);

        assertThat(result.nonce()).isNotBlank();
        assertThat(result.loginUrl()).contains("apiKey123");
    }

    @Test
    @DisplayName("complete: unknown nonce → throws IllegalStateException")
    void complete_invalidNonce_throws() {
        assertThatThrownBy(() -> zerodhaAuthService.complete("bad-nonce", "requestToken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("complete: valid nonce → exchanges token and marks connected")
    void complete_success_storesEncryptedAccessToken() {
        // Seed a nonce via initiate()
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithKey()));
        when(props.getLoginBaseUrl()).thenReturn("https://kite.zerodha.com/connect/login?v=3&api_key=");
        ZerodhaAuthService.OAuthInitResult init = zerodhaAuthService.initiate(1L);

        // Setup for complete()
        UserConfig config = configWithKey();
        config.setZerodhaApiSecret("encryptedSecret");
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(config));
        when(encryptionUtil.decrypt("encryptedSecret")).thenReturn("rawSecret");
        when(brokerAdapterFactory.exchangeToken("apiKey123", "rawSecret", "reqToken123"))
                .thenReturn("accessToken");
        when(encryptionUtil.encrypt("accessToken")).thenReturn("encryptedAccessToken");

        zerodhaAuthService.complete(init.nonce(), "reqToken123");

        assertThat(config.getZerodhaConnected()).isTrue();
        assertThat(config.getZerodhaAccessToken()).isEqualTo("encryptedAccessToken");
        verify(userConfigRepository).save(config);
    }

    @Test
    @DisplayName("isConnected: connected=true and access token present → returns true")
    void isConnected_trueWhenConnectedAndHasToken() {
        UserConfig config = configWithKey();
        config.setZerodhaConnected(true);
        config.setZerodhaAccessToken("someToken");
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(config));

        assertThat(zerodhaAuthService.isConnected(1L)).isTrue();
    }

    @Test
    @DisplayName("isConnected: connected=false → returns false")
    void isConnected_falseWhenDisconnected() {
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(configWithKey()));

        assertThat(zerodhaAuthService.isConnected(1L)).isFalse();
    }

    @Test
    @DisplayName("disconnect: clears access token and sets connected=false")
    void disconnect_clearsTokenAndSetsConnectedFalse() {
        UserConfig config = configWithKey();
        config.setZerodhaAccessToken("someToken");
        config.setZerodhaConnected(true);
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(config));

        zerodhaAuthService.disconnect(1L);

        assertThat(config.getZerodhaAccessToken()).isNull();
        assertThat(config.getZerodhaConnected()).isFalse();
        verify(userConfigRepository).save(config);
    }

    @Test
    @DisplayName("generateTotp: no TOTP secret configured → returns null")
    void generateTotp_noTotpSecret_returnsNull() {
        UserConfig config = configWithKey();
        config.setZerodhaTotpSecret(null);
        when(userConfigRepository.findByUser_Id(1L)).thenReturn(Optional.of(config));

        assertThat(zerodhaAuthService.generateTotp(1L)).isNull();
    }
}
