package com.trading.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.signals.Position;
import com.trading.signals.PositionRepository;
import com.trading.signals.PositionStatus;
import com.trading.signals.Signal;
import com.trading.signals.SignalRepository;
import com.trading.signals.SignalStatus;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.Optional;

/**
 * Polls Telegram for bot commands and routes them to the appropriate handler.
 *
 * <p>Only active when {@code telegram.enabled=true}. Uses long-poll interval of
 * 0 seconds (returns immediately) with a 10-second fixed-delay schedule to
 * avoid excessive API calls.
 *
 * <p>Supported commands: /portfolio /signals /summary /status
 *
 * <p>User lookup: the {@code from.id} in each Telegram message is matched against
 * {@code user_configs.telegram_chat_id}. Commands from unknown chat IDs are silently ignored.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramBotService {

    private final TelegramProperties     props;
    private final TelegramApiClient      telegramClient;
    private final UserConfigRepository   userConfigRepository;
    private final PositionRepository     positionRepository;
    private final SignalRepository       signalRepository;
    private final RestTemplate           restTemplate;
    private final ObjectMapper           objectMapper = new ObjectMapper();

    private long lastUpdateId = 0;

    public TelegramBotService(TelegramProperties props,
                              TelegramApiClient telegramClient,
                              UserConfigRepository userConfigRepository,
                              PositionRepository positionRepository,
                              SignalRepository signalRepository) {
        this.props               = props;
        this.telegramClient      = telegramClient;
        this.userConfigRepository = userConfigRepository;
        this.positionRepository  = positionRepository;
        this.signalRepository    = signalRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Scheduled(fixedDelay = 10_000)
    @Transactional(readOnly = true)
    public void pollUpdates() {
        if (!props.isEnabled() || props.getBotToken().isBlank()) return;

        String url = props.getBaseUrl() + "/bot" + props.getBotToken()
                + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=0&limit=100";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return;

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("ok").asBoolean(false)) return;

            for (JsonNode update : root.path("result")) {
                long updateId = update.path("update_id").asLong();
                if (updateId > lastUpdateId) lastUpdateId = updateId;
                processUpdate(update);
            }
        } catch (Exception e) {
            log.debug("Telegram poll error: {}", e.getMessage());
        }
    }

    private void processUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) return;

        String text   = message.path("text").asText("");
        String chatId = String.valueOf(message.path("chat").path("id").asLong());

        if (!text.startsWith("/")) return;

        String command = text.split("\\s+")[0].toLowerCase().replaceAll("@.*", "");

        Optional<UserConfig> configOpt = userConfigRepository.findAll().stream()
                .filter(c -> chatId.equals(c.getTelegramChatId()))
                .findFirst();

        if (configOpt.isEmpty()) {
            log.debug("Telegram command '{}' from unknown chatId={} — ignored", command, chatId);
            return;
        }

        UserConfig config = configOpt.get();
        Long userId = config.getUser().getId();

        String reply = switch (command) {
            case "/portfolio" -> buildPortfolioReply(userId);
            case "/signals"   -> buildSignalsReply();
            case "/summary"   -> buildSummaryReply(userId);
            case "/status"    -> buildStatusReply();
            default           -> "Unknown command. Try /portfolio /signals /summary /status";
        };

        telegramClient.sendMessage(chatId, reply);
    }

    // ── Command handlers ──────────────────────────────────────────────────────

    private String buildPortfolioReply(Long userId) {
        List<Position> active = positionRepository.findByUserIdAndStatus(userId, PositionStatus.ACTIVE);
        List<Position> pending = positionRepository.findByUserIdAndStatus(userId, PositionStatus.PENDING_ENTRY);

        if (active.isEmpty() && pending.isEmpty()) {
            return "No open positions.";
        }

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

    // ── Formatting helper ─────────────────────────────────────────────────────

    private String fmt(BigDecimal value) {
        if (value == null) return "—";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
