package com.trading.broker;

import com.trading.common.EncryptionUtil;
import com.trading.users.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrokerAdapterFactoryTest {

    @Mock ZerodhaProperties props;
    @Mock EncryptionUtil encryptionUtil;

    private BrokerAdapterFactory factory;

    @BeforeEach
    void setUp() {
        lenient().when(props.getBaseUrl()).thenReturn("https://api.kite.trade");
        lenient().when(props.getConnectTimeoutMs()).thenReturn(10_000);
        lenient().when(props.getReadTimeoutMs()).thenReturn(30_000);
        factory = new BrokerAdapterFactory(props, encryptionUtil);
    }

    @Test
    @DisplayName("forUser creates ZerodhaBrokerAdapter with decrypted token")
    void forUser_validConfig_returnsAdapter() {
        UserConfig config = UserConfig.builder()
                .zerodhaApiKey("apikey123")
                .zerodhaAccessToken("encrypted_token")
                .build();
        when(encryptionUtil.decrypt("encrypted_token")).thenReturn("plaintext_token");

        BrokerAdapter adapter = factory.forUser(config);

        assertThat(adapter).isInstanceOf(ZerodhaBrokerAdapter.class);
        verify(encryptionUtil).decrypt("encrypted_token");
    }

    @Test
    @DisplayName("forUser throws BrokerTokenException when API key missing")
    void forUser_noApiKey_throws() {
        UserConfig config = UserConfig.builder().build();

        assertThatThrownBy(() -> factory.forUser(config))
                .isInstanceOf(BrokerTokenException.class)
                .hasMessageContaining("API key");
    }

    @Test
    @DisplayName("forUser throws BrokerTokenException when access token missing")
    void forUser_noAccessToken_throws() {
        UserConfig config = UserConfig.builder()
                .zerodhaApiKey("apikey123")
                .build();

        assertThatThrownBy(() -> factory.forUser(config))
                .isInstanceOf(BrokerTokenException.class)
                .hasMessageContaining("access token");
    }
}
