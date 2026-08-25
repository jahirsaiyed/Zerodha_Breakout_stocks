package com.trading.notifications;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Sends a push notification to all registered devices for a user.
     * No-op if the user has no registered device tokens.
     * Failures per device are logged but do not throw.
     */
    public void sendToUser(Long userId, String title, String body, String deepLink) {
        if (FirebaseApp.getApps().isEmpty()) {
            return; // Firebase not configured
        }
        List<DeviceToken> devices = deviceTokenRepository.findByUser_Id(userId);
        if (devices.isEmpty()) return;

        for (DeviceToken device : devices) {
            try {
                Message message = Message.builder()
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("deepLink", deepLink)
                        .setToken(device.getToken())
                        .build();
                FirebaseMessaging.getInstance().send(message);
                log.debug("Push sent to userId={} platform={}", userId, device.getPlatform());
            } catch (Exception e) {
                log.warn("Push failed for userId={} platform={}: {}", userId, device.getPlatform(), e.getMessage());
            }
        }
    }
}
