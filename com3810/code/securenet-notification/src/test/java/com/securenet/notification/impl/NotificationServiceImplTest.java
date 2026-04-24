package com.securenet.notification.impl;

import com.securenet.model.EventType;
import com.securenet.model.SecurityEvent;
import com.securenet.storage.StorageGateway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Test
    void retrySweeper_deletesOutboxWhenRetrySucceeds() throws Exception {
        StorageGateway storageGateway = mock(StorageGateway.class);
        when(storageGateway.findPendingNotifications(50))
                .thenReturn(List.of(Map.of(
                        "notificationId", "notif-1",
                        "token", "token-a",
                        "payload", "{\"hello\":true}",
                        "attempts", 1
                )));

        NotificationServiceImpl service = new NotificationServiceImpl(storageGateway) {
            @Override
            public boolean dispatchViaApns(String apnsToken, String payload) {
                return true;
            }
        };
        service.setSweeperLeader(true);

        invokeSweepRetries(service);

        verify(storageGateway).deleteNotificationOutbox("notif-1");
        verify(storageGateway, never()).updateNotificationAttempts(anyString(), anyInt());
    }

    @Test
    void retrySweeper_incrementsAttemptsWhenRetryFails() throws Exception {
        StorageGateway storageGateway = mock(StorageGateway.class);
        when(storageGateway.findPendingNotifications(50))
                .thenReturn(List.of(Map.of(
                        "notificationId", "notif-2",
                        "token", "token-b",
                        "payload", "{\"hello\":false}",
                        "attempts", 2
                )));

        NotificationServiceImpl service = new NotificationServiceImpl(storageGateway) {
            @Override
            public boolean dispatchViaApns(String apnsToken, String payload) {
                return false;
            }
        };
        service.setSweeperLeader(true);

        invokeSweepRetries(service);

        verify(storageGateway).updateNotificationAttempts("notif-2", 3);
        verify(storageGateway, never()).deleteNotificationOutbox("notif-2");
    }

    private static void invokeSweepRetries(NotificationServiceImpl service) throws Exception {
        Method method = NotificationServiceImpl.class.getDeclaredMethod("sweepRetries");
        method.setAccessible(true);
        method.invoke(service);
    }
}
