package com.trading.zerodha;

import com.trading.broker.BrokerAdapterFactory;
import com.trading.broker.ZerodhaProperties;
import com.trading.common.EncryptionUtil;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the Zerodha OAuth 2-step flow:
 * <ol>
 *   <li>Build the Zerodha login URL for the user and store a nonce → userId mapping</li>
 *   <li>On callback, validate the nonce, exchange the request_token for an access_token,
 *       encrypt and persist it</li>
 * </ol>
 *
 * <p>Nonce state is kept in an in-memory map with a 10-minute TTL — suitable for a
 * small private system where only a handful of users log in concurrently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZerodhaAuthService {

    private static final int NONCE_TTL_SECONDS = 600; // 10 minutes

    private final ZerodhaProperties props;
    private final BrokerAdapterFactory brokerAdapterFactory;
    private final EncryptionUtil encryptionUtil;
    private final UserConfigRepository userConfigRepository;

    /** In-flight OAuth states: nonce → (userId, expiresAt) */
    private final ConcurrentHashMap<String, OAuthPending> pending = new ConcurrentHashMap<>();

    // ── Initiate ─────────────────────────────────────────────────────────────

    /**
     * Generate a nonce, store it, and return the Zerodha login URL.
     * The nonce will be stored as a short-lived cookie by the controller.
     */
    @Transactional(readOnly = true)
    public OAuthInitResult initiate(Long userId) {
        UserConfig config = userConfigRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalStateException("No user config for userId=" + userId));

        String apiKey = config.getZerodhaApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Configure your Zerodha API key in Settings before connecting.");
        }

        String nonce = UUID.randomUUID().toString();
        pending.put(nonce, new OAuthPending(userId, Instant.now().plusSeconds(NONCE_TTL_SECONDS)));

        String loginUrl = props.getLoginBaseUrl() + apiKey;
        return new OAuthInitResult(nonce, loginUrl);
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    /**
     * Exchange the Zerodha request_token for an access_token and persist it encrypted.
     *
     * @param nonce        the nonce from the OAuth cookie
     * @param requestToken the request_token provided by Zerodha in the callback
     */
    @Transactional
    public void complete(String nonce, String requestToken) {
        OAuthPending state = pending.remove(nonce);
        if (state == null) {
            throw new IllegalStateException("OAuth state not found — login may have expired, please try again.");
        }
        if (Instant.now().isAfter(state.expiresAt())) {
            throw new IllegalStateException("OAuth nonce expired — please start the Zerodha login again.");
        }

        UserConfig config = userConfigRepository.findByUser_Id(state.userId())
                .orElseThrow(() -> new IllegalStateException("User config not found for userId=" + state.userId()));

        String apiKey       = config.getZerodhaApiKey();
        String encApiSecret = config.getZerodhaApiSecret();
        if (encApiSecret == null || encApiSecret.isBlank()) {
            throw new IllegalStateException("Zerodha API secret is not configured for this user.");
        }

        String apiSecret    = encryptionUtil.decrypt(encApiSecret);
        String accessToken  = brokerAdapterFactory.exchangeToken(apiKey, apiSecret, requestToken);

        config.setZerodhaAccessToken(encryptionUtil.encrypt(accessToken));
        config.setZerodhaConnected(true);
        userConfigRepository.save(config);

        log.info("Zerodha OAuth completed successfully for userId={}", state.userId());
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isConnected(Long userId) {
        return userConfigRepository.findByUser_Id(userId)
                .map(c -> Boolean.TRUE.equals(c.getZerodhaConnected())
                        && c.getZerodhaAccessToken() != null)
                .orElse(false);
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    @Transactional
    public void disconnect(Long userId) {
        UserConfig config = userConfigRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalStateException("No user config for userId=" + userId));
        config.setZerodhaAccessToken(null);
        config.setZerodhaConnected(false);
        userConfigRepository.save(config);
        log.info("Zerodha disconnected for userId={}", userId);
    }

    // ── TOTP ──────────────────────────────────────────────────────────────────

    /**
     * Generate the current TOTP code for the user's stored TOTP secret.
     * Returns null if the user has not configured a TOTP secret.
     */
    @Transactional(readOnly = true)
    public String generateTotp(Long userId) {
        return userConfigRepository.findByUser_Id(userId)
                .map(UserConfig::getZerodhaTotpSecret)
                .filter(s -> s != null && !s.isBlank())
                .map(encryptedSecret -> TotpUtil.generate(encryptionUtil.decrypt(encryptedSecret)))
                .orElse(null);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredNonces() {
        Instant now = Instant.now();
        int removed = (int) pending.entrySet().stream()
                .filter(e -> now.isAfter(e.getValue().expiresAt()))
                .peek(e -> pending.remove(e.getKey()))
                .count();
        if (removed > 0) {
            log.debug("Cleaned {} expired OAuth nonces", removed);
        }
    }

    // ── Records ───────────────────────────────────────────────────────────────

    record OAuthInitResult(String nonce, String loginUrl) {}
    record OAuthPending(Long userId, Instant expiresAt) {}
}
