package com.trading.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    PushNotificationService pushService;

    @Test
    void sendToUser_isNoOpWhenNoDevicesRegistered() {
        when(deviceTokenRepository.findByUser_Id(1L)).thenReturn(List.of());
        // Should not throw
        pushService.sendToUser(1L, "Title", "Body", "zbs://dashboard");
        verify(deviceTokenRepository).findByUser_Id(1L);
    }
}
