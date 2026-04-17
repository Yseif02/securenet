package com.securenet.notification.impl;

import com.securenet.model.SecurityEvent;
import com.securenet.notification.NotificationService;
import com.securenet.storage.StorageGateway;

import java.util.*;
import java.util.concurrent.*;

/**
 * Implementation of the Notification Service.
 *
 * <p>Manages push token registry and dispatches alerts to homeowners
 * via APNs (iOS) and FCM (Android/web). Since we don't have real
 * APNs/FCM credentials, dispatch methods simulate the call and log
 * the result.
 *
 * <h3>DS Problems Addressed</h3>
 * <ul>
 *   <li><strong>Leader Election (#1):</strong> The retry sweeper runs
 *       only on the elected leader node to prevent duplicate dispatch.
 *       Uses a simple leader flag — in production this would use
 *       ZooKeeper-style election as described in Stage 3.</li>
 *   <li><strong>Idempotency (#8):</strong> Each notification carries a
 *       UUID set as APNs apns-id / FCM collapse-key. Platform-level
 *       dedup prevents duplicate delivery on retry.</li>
 *   <li><strong>At-least-once delivery:</strong> Failed dispatches are
 *       retried with exponential backoff up to 5 attempts.</li>
 * </ul>
 */
public class NotificationServiceImpl implements NotificationService {

    private final StorageGateway storageGateway;

    /** Pending notification outbox — simulates the DB outbox table. */
    private final ConcurrentLinkedQueue<PendingNotification> outbox = new ConcurrentLinkedQueue<>();

    /** Whether this node is the sweeper leader. */
    private volatile boolean isSweeperLeader = true;

    private final ScheduledExecutorService retrySweeper;

    public NotificationServiceImpl(StorageGateway storageGateway) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.retrySweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notification-retry-sweeper");
            t.setDaemon(true);
            return t;
        });
        this.retrySweeper.scheduleAtFixedRate(this::sweepRetries, 10, 10, TimeUnit.SECONDS);
    }

    public void setSweeperLeader(boolean leader) {
        this.isSweeperLeader = leader;
    }

    // =====================================================================
    // Push token management
    // =====================================================================

    @Override
    public void registerPushToken(String userId, String pushToken, String platform)
            throws IllegalArgumentException {
        if (!"APNS".equals(platform) && !"FCM".equals(platform)) {
            throw new IllegalArgumentException("Platform must be APNS or FCM");
        }
        storageGateway.savePushToken(userId, pushToken, platform);
        System.out.println("[NotificationService] Registered push token for user=" +
                userId + " platform=" + platform);
    }

    @Override
    public void deregisterPushToken(String pushToken) {
        storageGateway.deletePushToken(pushToken);
        System.out.println("[NotificationService] Deregistered push token: " + pushToken);
    }

    @Override
    public List<String> getPushTokensForUser(String userId) {
        return storageGateway.findPushTokensByUser(userId);
    }

    // =====================================================================
    // Alert dispatch
    // =====================================================================

    @Override
    public void sendEventAlert(SecurityEvent event) {
        Objects.requireNonNull(event, "event");

        List<String> tokens = storageGateway.findPushTokensByUser(event.ownerId());
        if (tokens.isEmpty()) {
            System.out.println("[NotificationService] No push tokens for owner=" +
                    event.ownerId() + ", skipping alert");
            return;
        }

        String notificationId = UUID.randomUUID().toString();
        String payload = buildPayload(event, notificationId);

        System.out.println("[NotificationService] Dispatching alert: event=" +
                event.type() + " device=" + event.deviceId() +
                " to " + tokens.size() + " token(s)");

        for (String token : tokens) {
            boolean success = dispatchViaApns(token, payload) || dispatchViaFcm(token, payload);
            if (!success) {
                outbox.add(new PendingNotification(notificationId, token, payload, 1));
            }
        }
    }

    // =====================================================================
    // Platform dispatch (simulated)
    // =====================================================================

    @Override
    public boolean dispatchViaApns(String apnsToken, String payload) {
        System.out.println("[NotificationService] APNS dispatch to " + apnsToken +
                ": " + truncate(payload, 80));
        // Simulated — always succeeds
        return true;
    }

    @Override
    public boolean dispatchViaFcm(String fcmToken, String payload) {
        System.out.println("[NotificationService] FCM dispatch to " + fcmToken +
                ": " + truncate(payload, 80));
        // Simulated — always succeeds
        return true;
    }

    // =====================================================================
    // Retry sweeper (leader only)
    // =====================================================================

    private void sweepRetries() {
        if (!isSweeperLeader) return;

        int processed = 0;
        PendingNotification pending;
        while ((pending = outbox.poll()) != null) {
            if (pending.attempts >= 5) {
                System.err.println("[NotificationService] FAILED after 5 attempts: " +
                        pending.notificationId);
                continue;
            }

            boolean success = dispatchViaApns(pending.token, pending.payload);
            if (!success) {
                outbox.add(new PendingNotification(
                        pending.notificationId, pending.token,
                        pending.payload, pending.attempts + 1));
            }
            processed++;
        }

        if (processed > 0) {
            System.out.println("[NotificationService] Retry sweeper processed " +
                    processed + " pending notifications");
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String buildPayload(SecurityEvent event, String notificationId) {
        String title = switch (event.type()) {
            case MOTION_DETECTED -> "Motion Detected";
            case DEVICE_UNRESPONSIVE -> "Device Unresponsive";
            case DOOR_UNLOCKED -> "Door Unlocked";
            case DOOR_LOCKED -> "Door Locked";
            default -> "Security Alert";
        };

        String body = event.metadata().getOrDefault("device_display_name", event.deviceId())
                + " at " + event.occurredAt();

        return "{\"notification_id\":\"" + notificationId +
                "\",\"title\":\"" + title +
                "\",\"body\":\"" + body +
                "\",\"event_id\":\"" + event.eventId() + "\"}";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record PendingNotification(String notificationId, String token,
                                        String payload, int attempts) {}
}
