package com.trading.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.EncryptionUtil;
import com.trading.signals.Position;
import com.trading.signals.PositionRepository;
import com.trading.signals.PositionStatus;
import com.trading.signals.Signal;
import com.trading.signals.SignalRepository;
import com.trading.signals.SignalStatus;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls each user's personal Telegram bot for commands and routes them to the
 * appropriate handler.
 *
 * <p>Users configure their own bot token in Settings. The poll loop runs every
 * 10 s and iterates all {@link UserConfig} rows that have a {@code telegram_bot_token}
 * set. Each user's bot is polled independently with its own {@code lastUpdateId} cursor.
 *
 * <p>Supported commands: /portfolio /signals /summary /status
 *
 * <p>User lookup: the {@code from.id} in each Telegram message is matched against
 * {@code user_configs.telegram_chat_id} to identify the requesting user.
 *
 * <p>Chat discovery: each update is stored in {@link #discoveredChatsByUser} keyed
 * by {@code userId} so the UI can present a per-user chat picker.
 */
@Slf4j
@Service
public class TelegramBotService {

    private final TelegramProperties       telegramProperties;
    private final TelegramApiClient        telegramClient;
    private final UserConfigRepository     userConfigRepository;
    private final PositionRepository       positionRepository;
    private final SignalRepository         signalRepository;
    private final EncryptionUtil           encryptionUtil;
    private final RestTemplate             restTemplate;
    private final ObjectMapper             objectMapper = new ObjectMapper();

    /** userId → (chatId → TelegramChatDto), populated as updates arrive per user's bot. */
    private final Map<Long, Map<String, TelegramChatDto>> discoveredChatsByUser = new ConcurrentHashMap<>();

    /** userId → last processed update_id for that user's bot. */
    private final Map<Long, Long> lastUpdateIdByUser = new ConcurrentHashMap<>();

    public TelegramBotService(TelegramProperties telegramProperties,
                              TelegramApiClient telegramClient,
                              UserConfigRepository userConfigRepository,
                              PositionRepository positionRepository,
                              SignalRepository signalRepository,
                              EncryptionUtil encryptionUtil) {
        this.telegramProperties   = telegramProperties;
        this.telegramClient       = telegramClient;
        this.userConfigRepository = userConfigRepository;
        this.positionRepository   = positionRepository;
        this.signalRepository     = signalRepository;
        this.encryptionUtil       = encryptionUtil;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Scheduled(fixedDelay = 10_000)
    @Transactional(readOnly = true)
    public void pollUpdates() {
        List<UserConfig> configs = userConfigRepository.findAll().stream()
                .filter(c -> c.getTelegramBotToken() != null)
                .toList();

        for (UserConfig config : configs) {
            try {
                String token = encryptionUtil.decrypt(config.getTelegramBotToken());
                pollUserBot(config, token);
            } catch (Exception e) {
                log.debug("Failed to poll bot for user {}: {}", config.getUser().getId(), e.getMessage());
            }
        }
    }

    private void pollUserBot(UserConfig config, String token) {
        Long userId = config.getUser().getId();
        long lastId = lastUpdateIdByUser.getOrDefault(userId, 0L);

        String url = telegramProperties.getBaseUrl() + "/bot" + token
                + "/getUpdates?offset=" + (lastId + 1) + "&timeout=0&limit=100";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return;

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("ok").asBoolean(false)) return;

            for (JsonNode update : root.path("result")) {
                long updateId = update.path("update_id").asLong();
                if (updateId > lastId) {
                    lastId = updateId;
                }
                recordChat(userId, update);
                processUpdate(config, token, update);
            }
            lastUpdateIdByUser.put(userId, lastId);
        } catch (Exception e) {
            log.debug("Telegram poll error for user {}: {}", userId, e.getMessage());
        }
    }

    private void recordChat(Long userId, JsonNode update) {
        JsonNode chat = update.path("message").path("chat");
        if (chat.isMissingNode()) {
            chat = update.path("channel_post").path("chat");
        }
        if (chat.isMissingNode()) return;

        String chatId    = String.valueOf(chat.path("id").asLong());
        String chatType  = chat.path("type").asText("private");
        String chatTitle = resolveChatTitle(chat, chatType);

        discoveredChatsByUser
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(chatId, new TelegramChatDto(chatId, chatTitle, chatType));
    }

    private String resolveChatTitle(JsonNode chat, String chatType) {
        if (!chatType.equals("private")) {
            String title = chat.path("title").asText("");
            if (!title.isBlank()) return title;
        }
        String firstName = chat.path("first_name").asText("");
        String lastName  = chat.path("last_name").asText("");
        String username  = chat.path("username").asText("");
        String fullName  = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) return fullName;
        if (!username.isBlank()) return "@" + username;
        return chatType;
    }

    /**
     * Returns the Telegram chats discovered so far for the given user's bot.
     * The list is in-memory and resets on application restart.
     */
    public List<TelegramChatDto> getDiscoveredChatsForUser(Long userId) {
        Map<String, TelegramChatDto> chats = discoveredChatsByUser.get(userId);
        return chats == null ? List.of() : List.copyOf(chats.values());
    }

    private void processUpdate(UserConfig config, String token, JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) return;

        String text   = message.path("text").asText("");
        String chatId = String.valueOf(message.path("chat").path("id").asLong());

        if (!text.startsWith("/")) return;

        String command = text.split("\\s+")[0].toLowerCase().replaceAll("@.*", "");

        // Only respond to messages from the configured chat (or from the user themselves)
        if (!chatId.equals(config.getTelegramChatId())) {
            log.debug("Telegram command '{}' from chatId={} does not match user {}'s configured chatId — ignored",
                    command, chatId, config.getUser().getId());
            return;
        }

        Long userId = config.getUser().getId();
        String reply = switch (command) {
            case "/portfolio" -> buildPortfolioReply(userId);
            case "/signals"   -> buildSignalsReply();
            case "/summary"   -> buildSummaryReply(userId);
            case "/status"    -> buildStatusReply();
            default           -> "Unknown command. Try /portfolio /signals /summary /status";
        };

        telegramClient.sendMessage(token, chatId, reply);
    }

    // ── Command handlers ──────────────────────────────────────────────────────

    private String buildPortfolioReply(Long userId) {
        List<Position> active  = positionRepository.findByUserIdAndStatus(userId, PositionStatus.ACTIVE);
        List<Position> pending = positionRepository.findByUserIdAndStatus(userId, PositionStatus.PENDING_ENTRY);

        if (active.isEmpty() && pending.isEmpty()) return "No open positions.";

        StringBuilder sb = new StringBuilder("Portfolio\n─────────────────\n");
        if (!active.isEmpty()) {
            sb.append("Active (").append(active.size()).append("):\n");
            for (Position p : active) {
                sb.append("  ").append(p.getSymbol())
                  .append("  qty=").append(p.getQuantity())
                  .append("  entry=").append(fmt(p.getAvgEntryPrice()))
                  .append("\n");
            }
        }
        if (!pending.isEmpty()) {
            sb.append("\nPending entry (").append(pending.size()).append("):\n");
            for (Position p : pending) {
                sb.append("  ").append(p.getSymbol())
                  .append("  qty=").append(p.getQuantity())
                  .append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String buildSignalsReply() {
        List<Signal> signals = signalRepository.findByStatus(SignalStatus.ACTIVE);
        if (signals.isEmpty()) return "No active signals.";

        StringBuilder sb = new StringBuilder("Active signals (").append(signals.size()).append("):\n");
        for (Signal s : signals) {
            sb.append("  ").append(s.getSymbol())
              .append("  entry=").append(fmt(s.getEntryPrice()))
              .append("  R:R=").append(s.getRiskRewardRatio().setScale(2, RoundingMode.HALF_UP))
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String buildSummaryReply(Long userId) {
        List<Position> closedPositions = positionRepository.findByUserIdAndStatusIn(userId,
                List.of(PositionStatus.CLOSED_TARGET, PositionStatus.CLOSED_SL, PositionStatus.CLOSED_MANUAL));

        BigDecimal totalPnl = closedPositions.stream()
                .map(p -> p.getRealisedPnl() != null ? p.getRealisedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int wins   = (int) closedPositions.stream().filter(p -> p.getStatus() == PositionStatus.CLOSED_TARGET).count();
        int losses = (int) closedPositions.stream().filter(p -> p.getStatus() == PositionStatus.CLOSED_SL).count();

        String sign = totalPnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return "Summary\n─────────────────\n"
                + "Closed trades: " + closedPositions.size() + "  (" + wins + "W / " + losses + "L)\n"
                + "Total P&L: " + sign + totalPnl.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String buildStatusReply() {
        return "System Status\n─────────────────\n"
                + "Bot: Online\n"
                + "Time: " + Instant.now() + "\n"
                + "Use /portfolio, /signals, or /summary for trading data.";
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "—";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
