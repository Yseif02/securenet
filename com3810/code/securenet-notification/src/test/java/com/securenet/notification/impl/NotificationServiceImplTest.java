package com.securenet.notification.impl;

import com.securenet.model.EventType;
import com.securenet.model.SecurityEvent;
import com.securenet.storage.StorageGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    @Test
    void sendEventAlert_queuesRetryWhenDispatchFails() {
        StorageGateway storageGateway = mock(StorageGateway.class);
        when(storageGateway.findPushTokensByUser("owner-1"))
                .thenReturn(List.of("token-a", "token-b"));

        NotificationServiceImpl service = new NotificationServiceImpl(storageGateway) {
            @Override
            public boolean dispatchViaApns(String apnsToken, String payload) {
                return false;
            }

            @Override
            public boolean dispatchViaFcm(String fcmToken, String payload) {
                return false;
            }
        };
        service.setSweeperLeader(false);

        SecurityEvent event = new SecurityEvent("event-1", "device-1", "owner-1",
                EventType.MOTION_DETECTED, Instant.now(), java.util.Map.of());

        service.sendEventAlert(event);

        verify(storageGateway, times(2))
                .saveNotificationOutbox(anyString(), anyString(), contains("\"event_id\":\"event-1\""), eq(1));
    }
}
