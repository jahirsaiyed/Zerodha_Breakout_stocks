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
     */
    @Transactional(readOnly = true)
    public void notifyUser(Long userId, String message) {
        userConfigRepository.findByUser_Id(userId).ifPresentOrElse(config -> {
            String chatId = config.getTelegramChatId();
            if (chatId == null || chatId.isBlank()) {
                log.debug("No Telegram chatId for user {} — notification skipped", userId);
                return;
            }
            telegramClient.sendMessage(chatId, message);
        }, () -> log.debug("No user config for user {} — notification skipped", userId));
    }
}
