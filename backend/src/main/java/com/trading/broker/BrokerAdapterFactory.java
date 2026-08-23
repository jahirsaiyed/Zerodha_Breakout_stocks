package com.trading.broker;

import com.trading.common.EncryptionUtil;
import com.trading.users.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Creates a per-user {@link BrokerAdapter} from that user's Zerodha credentials.
 *
 * <p>Decrypts the stored access token before constructing the adapter.
 * Throws {@link BrokerTokenException} if the user has no access token configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerAdapterFactory {

    private final ZerodhaProperties props;
    private final EncryptionUtil encryptionUtil;

    /**
     * Exchanges a Zerodha request_token for an access_token using the user's API key and secret.
     * Used by the OAuth callback flow — the resulting token is then stored encrypted.
     */
    public String exchangeToken(String apiKey, String apiSecret, String requestToken) {
        ZerodhaApiClient client = new ZerodhaApiClient(
                apiKey, "", props.getBaseUrl(), props.getOrderBaseUrl(),
                props.getConnectTimeoutMs(), props.getReadTimeoutMs());
        return client.refreshAccessToken(apiKey, apiSecret, requestToken);
    }

    public BrokerAdapter forUser(UserConfig config) {
        String apiKey = props.getApiKey();
        String encryptedToken = config.getZerodhaAccessToken();

        if (apiKey == null || apiKey.isBlank()) {
            throw new BrokerTokenException("Zerodha API key is not configured on the server");
        }
        if (encryptedToken == null || encryptedToken.isBlank()) {
            throw new BrokerTokenException("User has no Zerodha access token — please re-login via Settings");
        }

        String accessToken = encryptionUtil.decrypt(encryptedToken);

        ZerodhaApiClient client = new ZerodhaApiClient(
                apiKey, accessToken,
                props.getBaseUrl(), props.getOrderBaseUrl(),
                props.getConnectTimeoutMs(), props.getReadTimeoutMs());

        log.debug("Created ZerodhaBrokerAdapter for user config id={}", config.getId());
        return new ZerodhaBrokerAdapter(client);
    }
}
