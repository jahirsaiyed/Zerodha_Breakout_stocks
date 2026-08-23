package com.trading.notifications;

import com.trading.signals.PositionRepository;
import com.trading.users.UserConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes notification messages to the right user's Telegram chat.
 *
 * Two entry points:
 * <ul>
 *   <li>{@link #notifyForPosition(Long, String)} — resolves the owner of a position then sends</li>
 *   <li>{@link #notifyUser(Long, String)} — sends directly given a userId</li>
 * </ul>
 *
 * <p>If Telegram signals that a group was upgraded to a supergroup
 * ({@code migrate_to_chat_id}), the stored chat ID is automatically updated
 * so subsequent notifications go to the correct chat without manual intervention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final TelegramApiClient telegramClient;
    private final PositionRepository positionRepository;
    private final UserConfigRepository userConfigRepository;

    /**
     * Looks up the owning user for {@code positionId} and sends them a Telegram message.
     * No-op if the position is not found.
     */
    @Transactional(readOnly = true)
    public void notifyForPosition(Long positionId, String message) {
        positionRepository.findById(positionId).ifPresentOrElse(
                pos -> notifyUser(pos.getUser().getId(), message),
                () -> log.debug("Notification skipped — position {} not found", positionId)
        );
    }

    /**
     * Sends a Telegram message to the user identified by {@code userId}.
     * No-op if the user has no Telegram chat ID configured.
     *
     * <p>If the stored chat ID is stale due to a group→supergroup migration,
     * the config is updated automatically with the new chat ID.
     */
    @Transactional
    public void notifyUser(Long userId, String message) {
        userConfigRepository.findByUser_Id(userId).ifPresentOrElse(config -> {
            String chatId = config.getTelegramChatId();
            if (chatId == null || chatId.isBlank()) {
                log.debug("No Telegram chatId for user {} — notification skipped", userId);
                return;
            }
            String effectiveChatId = telegramClient.sendMessage(chatId, message);
            if (effectiveChatId != null && !effectiveChatId.equals(chatId)) {
                log.info("Updating stored Telegram chatId for user {} from {} to {}",
                        userId, chatId, effectiveChatId);
                config.setTelegramChatId(effectiveChatId);
                userConfigRepository.save(config);
            }
        }, () -> log.debug("No user config for user {} — notification skipped", userId));
    }
}
