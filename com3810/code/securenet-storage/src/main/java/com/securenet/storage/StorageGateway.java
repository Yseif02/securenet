package com.securenet.storage;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.model.*;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.VideoNotFoundException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * HTTP client that proxies all storage operations to the remote
 * {@link com.securenet.storage.server.StorageServiceServer}.
 *
 * <p>This replaces the old in-process {@code StorageGateway} which held
 * a direct reference to the storage implementation. Every method now
 * makes an HTTP/JSON call over the network.
 *
 * <p>Services create a {@code StorageGateway} with the URL of the
 * storage service (e.g. {@code "http://localhost:9000"}) and call the
 * same methods they always have — the distributed transport is hidden
 * behind this facade.
 */
public class StorageGateway {

    private final String baseUrl;
    private final ServiceClient client;

    /**
     * @param storageServiceUrl root URL of the Storage Service, e.g.
     *                          {@code "http://localhost:9000"}
     */
    public StorageGateway(String storageServiceUrl) {
        this.baseUrl = Objects.requireNonNull(storageServiceUrl, "storageServiceUrl");
        this.client = new ServiceClient();
    }

    // =====================================================================
    // User data + auth helpers
    // =====================================================================

    public void saveUser(User user) {
        ServiceResponse resp = post("/storage/users", user);
        if (!resp.isSuccess() && resp.statusCode() == 409) {
            throw new IllegalArgumentException(extractError(resp));
        }
    }

    public Optional<User> findUserById(String userId) {
        ServiceResponse resp = get("/storage/users/" + userId);
        if (resp.statusCode() == 404) return Optional.empty();
        return Optional.of(resp.bodyAs(User.class));
    }

    public Optional<User> findUserByEmail(String email) {
        String encoded = URLEncoder.encode(email, StandardCharsets.UTF_8);
        ServiceResponse resp = get("/storage/users/email/" + encoded);
        if (resp.statusCode() == 404) return Optional.empty();
        return Optional.of(resp.bodyAs(User.class));
    }

    public void updateUser(User user) {
        put("/storage/users", user);
    }

    public void savePasswordHash(String userId, String passwordHash) {
        post("/storage/passwords/" + userId, Map.of("hash", passwordHash));
    }

    public Optional<String> findPasswordHashByUserId(String userId) {
        ServiceResponse resp = get("/storage/passwords/" + userId);
        if (resp.statusCode() == 404) return Optional.empty();
        Map map = resp.bodyAs(Map.class);
        return Optional.ofNullable((String) map.get("hash"));
    }

    public void saveAuthToken(AuthToken token) {
        post("/storage/auth-tokens", token);
    }

    public Optional<AuthToken> findAuthToken(String tokenValue) {
        String encoded = URLEncoder.encode(tokenValue, StandardCharsets.UTF_8);
        ServiceResponse resp = get("/storage/auth-tokens/" + encoded);
        if (resp.statusCode() == 404) return Optional.empty();
        return Optional.of(resp.bodyAs(AuthToken.class));
    }

    public void revokeAuthToken(String tokenValue) {
        String encoded = URLEncoder.encode(tokenValue, StandardCharsets.UTF_8);
        post("/storage/auth-tokens/" + encoded + "/revoke", Map.of());
    }

    public boolean isTokenRevoked(String tokenValue) {
        String encoded = URLEncoder.encode(tokenValue, StandardCharsets.UTF_8);
        ServiceResponse resp = get("/storage/auth-tokens/" + encoded + "/revoked");
        Map map = resp.bodyAs(Map.class);
        return Boolean.TRUE.equals(map.get("revoked"));
    }

    // =====================================================================
    // Device state
    // =====================================================================

    public void saveDevice(Device device) {
        ServiceResponse resp = post("/storage/devices", device);
        if (resp.statusCode() == 409) {
            throw new IllegalArgumentException(extractError(resp));
        }
    }

    public Optional<Device> findDeviceById(String deviceId) {
        ServiceResponse resp = get("/storage/devices/" + deviceId);
        if (resp.statusCode() == 404) return Optional.empty();
        return Optional.of(resp.bodyAs(Device.class));
    }

    public List<Device> findDevicesByOwner(String ownerId) {
        ServiceResponse resp = get("/storage/devices/owner/" + ownerId);
        return JsonUtil.gson().fromJson(resp.body(),
                new TypeToken<List<Device>>(){}.getType());
    }

    public void updateDevice(Device device) throws DeviceNotFoundException {
        ServiceResponse resp = put("/storage/devices", device);
        if (resp.statusCode() == 404) {
            throw new DeviceNotFoundException(device.deviceId());
        }
    }

    public void deleteDevice(String deviceId) throws DeviceNotFoundException {
        ServiceResponse resp = delete("/storage/devices/" + deviceId);
        if (resp.statusCode() == 404) {
            throw new DeviceNotFoundException(deviceId);
        }
    }

    // =====================================================================
    // Event history
    // =====================================================================

    public void saveEvent(SecurityEvent event) {
        post("/storage/events", event);
    }

    public Optional<SecurityEvent> findEventById(String eventId) {
        ServiceResponse resp = get("/storage/events/" + eventId);
        if (resp.statusCode() == 404) return Optional.empty();
        return Optional.of(resp.bodyAs(SecurityEvent.class));
    }

    public List<SecurityEvent> findEventsByDevice(String deviceId, Instant from, Instant to, int maxEvents) {
        String url = "/storage/events/device/" + deviceId +
                "?from=" + from + "&to=" + to + "&max=" + maxEvents;
        ServiceResponse resp = get(url);
        return JsonUtil.gson().fromJson(resp.body(),
                new TypeToken<List<SecurityEvent>>(){}.getType());
    }

    public List<SecurityEvent> findEventsByOwnerAndType(String ownerId, String eventType,
                                                         Instant from, Instant to, int maxEvents) {
        String url = "/storage/events/owner/" + ownerId + "/type/" + eventType +
                "?from=" + from + "&to=" + to + "&max=" + maxEvents;
        ServiceResponse resp = get(url);
        return JsonUtil.gson().fromJson(resp.body(),
                new TypeToken<List<SecurityEvent>>(){}.getType());
    }

    // =====================================================================
    // Video archive
    // =====================================================================

    public void saveVideoClip(VideoClip clip, byte[] rawBytes) {
        Map<String, Object> body = new HashMap<>();
        body.put("clip", clip);
        body.put("bytes", Base64.getEncoder().encodeToString(rawBytes));
        post("/storage/video-clips", body);
    }

    public Optional<VideoClip> findClipById(String clipId) {
        ServiceResponse resp = get("/storage/video-clips/" + clipId);
        if (resp.statusCode() == 404) return Optional.empty();
        return Optional.of(resp.bodyAs(VideoClip.class));
    }

    public List<VideoClip> findClipsByDevice(String deviceId, Instant from, Instant to) {
        String url = "/storage/video-clips/device/" + deviceId +
                "?from=" + from + "&to=" + to;
        ServiceResponse resp = get(url);
        return JsonUtil.gson().fromJson(resp.body(),
                new TypeToken<List<VideoClip>>(){}.getType());
    }

    public byte[] loadVideoBytes(String storageKey) throws VideoNotFoundException {
        String encoded = URLEncoder.encode(storageKey, StandardCharsets.UTF_8);
        ServiceResponse resp = get("/storage/video-bytes/" + encoded);
        if (resp.statusCode() == 404) {
            throw new VideoNotFoundException(storageKey);
        }
        Map map = resp.bodyAs(Map.class);
        return Base64.getDecoder().decode((String) map.get("bytes"));
    }

    // =====================================================================
    // Firmware + push tokens
    // =====================================================================

    public String getLatestFirmwareVersion(String deviceTypeKey) {
        ServiceResponse resp = get("/storage/firmware/" + deviceTypeKey + "/latest");
        if (resp.statusCode() == 404) return null;
        Map map = resp.bodyAs(Map.class);
        return (String) map.get("version");
    }

    public byte[] loadFirmwareBinary(String deviceTypeKey, String firmwareVersion) {
        ServiceResponse resp = get("/storage/firmware/" + deviceTypeKey + "/" + firmwareVersion);
        if (resp.statusCode() == 404) {
            throw new IllegalArgumentException("Firmware not found: " + deviceTypeKey + ":" + firmwareVersion);
        }
        Map map = resp.bodyAs(Map.class);
        return Base64.getDecoder().decode((String) map.get("bytes"));
    }

    public void saveFirmwareBinary(String deviceTypeKey, String firmwareVersion, byte[] binaryBytes) {
        String b64 = Base64.getEncoder().encodeToString(binaryBytes);
        post("/storage/firmware/" + deviceTypeKey + "/" + firmwareVersion,
                Map.of("bytes", b64));
    }

    public void savePushToken(String userId, String token, String platform) {
        post("/storage/push-tokens",
                Map.of("userId", userId, "token", token, "platform", platform));
    }

    public void deletePushToken(String token) {
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        delete("/storage/push-tokens/" + encoded);
    }

    public List<String> findPushTokensByUser(String userId) {
        ServiceResponse resp = get("/storage/push-tokens/user/" + userId);
        return JsonUtil.gson().fromJson(resp.body(),
                new TypeToken<List<String>>(){}.getType());
    }

    // =====================================================================
    // DMS registration tokens
    // =====================================================================

    public void saveRegistrationToken(String deviceId, String registrationToken) {
        post("/storage/registration-tokens",
                Map.of("deviceId", deviceId, "registrationToken", registrationToken));
    }

    public Optional<String> findRegistrationToken(String deviceId) {
        ServiceResponse resp = get("/storage/registration-tokens/" + deviceId);
        if (resp.statusCode() == 404) return Optional.empty();
        Map map = resp.bodyAs(Map.class);
        return Optional.ofNullable((String) map.get("registrationToken"));
    }

    public void deleteRegistrationToken(String deviceId) {
        delete("/storage/registration-tokens/" + deviceId);
    }

    // =====================================================================
    // DMS device heartbeats
    // =====================================================================

    public void saveDeviceHeartbeat(String deviceId, Instant heartbeatAt) {
        post("/storage/device-heartbeats",
                Map.of("deviceId", deviceId, "heartbeatAt", heartbeatAt.toString()));
    }

    public Optional<Instant> findDeviceHeartbeat(String deviceId) {
        ServiceResponse resp = get("/storage/device-heartbeats/" + deviceId);
        if (resp.statusCode() == 404) return Optional.empty();
        Map map = resp.bodyAs(Map.class);
        return Optional.of(Instant.parse((String) map.get("heartbeatAt")));
    }

    // =====================================================================
    // EPS deduplication
    // =====================================================================

    public void saveDeduplicationEntry(String dedupKey, String eventId, Instant recordedAt) {
        post("/storage/eps-dedup",
                Map.of("dedupKey", dedupKey, "eventId", eventId, "recordedAt", recordedAt.toString()));
    }

    public Optional<Map<String, String>> findDeduplicationEntry(String dedupKey) {
        String encoded = URLEncoder.encode(dedupKey, StandardCharsets.UTF_8);
        ServiceResponse resp = get("/storage/eps-dedup/" + encoded);
        if (resp.statusCode() == 404) return Optional.empty();
        Map map = resp.bodyAs(Map.class);
        return Optional.of(Map.of("eventId", (String) map.get("eventId"),
                "recordedAt", (String) map.get("recordedAt")));
    }

    public int deleteExpiredDeduplicationEntries(Instant olderThan) {
        ServiceResponse resp = post("/storage/eps-dedup/cleanup",
                Map.of("olderThan", olderThan.toString()));
        Map map = resp.bodyAs(Map.class);
        return ((Number) map.get("deleted")).intValue();
    }

    // =====================================================================
    // EPS motion cooldown
    // =====================================================================

    public void saveMotionCooldown(String deviceId, Instant alertAt) {
        post("/storage/eps-motion-cooldown",
                Map.of("deviceId", deviceId, "alertAt", alertAt.toString()));
    }

    public Optional<Instant> findMotionCooldown(String deviceId) {
        ServiceResponse resp = get("/storage/eps-motion-cooldown/" + deviceId);
        if (resp.statusCode() == 404) return Optional.empty();
        Map map = resp.bodyAs(Map.class);
        return Optional.of(Instant.parse((String) map.get("alertAt")));
    }

    // =====================================================================
    // EPS Lamport clock
    // =====================================================================

    public void saveLamportClock(String nodeId, long value) {
        post("/storage/eps-lamport-clock", Map.of("nodeId", nodeId, "value", value));
    }

    public long findLamportClock(String nodeId) {
        ServiceResponse resp = get("/storage/eps-lamport-clock/" + nodeId);
        if (resp.statusCode() == 404) return 0L;
        Map map = resp.bodyAs(Map.class);
        return ((Number) map.get("value")).longValue();
    }

    // =====================================================================
    // VSS recording sessions
    // =====================================================================

    public void saveRecordingSession(String sessionId, String deviceId, String ownerId, Instant startedAt) {
        post("/storage/recording-sessions", Map.of(
                "sessionId", sessionId, "deviceId", deviceId,
                "ownerId", ownerId, "startedAt", startedAt.toString()));
    }

    public Optional<Map<String, String>> findRecordingSession(String sessionId) {
        ServiceResponse resp = get("/storage/recording-sessions/" + sessionId);
        if (resp.statusCode() == 404) return Optional.empty();
        return Optional.of(resp.bodyAs(Map.class));
    }

    public Optional<String> findActiveSessionForDevice(String deviceId) {
        ServiceResponse resp = get("/storage/recording-sessions/device/" + deviceId);
        if (resp.statusCode() == 404) return Optional.empty();
        Map map = resp.bodyAs(Map.class);
        return Optional.ofNullable((String) map.get("sessionId"));
    }

    public void deleteRecordingSession(String sessionId) {
        delete("/storage/recording-sessions/" + sessionId);
    }

    // =====================================================================
    // Notification outbox
    // =====================================================================

    public void saveNotificationOutbox(String notificationId, String token, String payload, int attempts) {
        post("/storage/notification-outbox", Map.of(
                "notificationId", notificationId, "token", token,
                "payload", payload, "attempts", attempts));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findPendingNotifications(int maxResults) {
        ServiceResponse resp = get("/storage/notification-outbox?max=" + maxResults);
        return JsonUtil.gson().fromJson(resp.body(),
                new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType());
    }

    public void deleteNotificationOutbox(String notificationId) {
        delete("/storage/notification-outbox/" + notificationId);
    }

    public void updateNotificationAttempts(String notificationId, int newAttempts) {
        post("/storage/notification-outbox/" + notificationId + "/attempts",
                Map.of("attempts", newAttempts));
    }

    // =====================================================================
    // HTTP helpers
    // =====================================================================

    private ServiceResponse get(String path) {
        try {
            return client.get(baseUrl + path);
        } catch (IOException e) {
            throw new RuntimeException("StorageService unreachable: " + e.getMessage(), e);
        }
    }

    private ServiceResponse post(String path, Object body) {
        try {
            return client.post(baseUrl + path, body);
        } catch (IOException e) {
            throw new RuntimeException("StorageService unreachable: " + e.getMessage(), e);
        }
    }

    private ServiceResponse put(String path, Object body) {
        try {
            return client.put(baseUrl + path, body);
        } catch (IOException e) {
            throw new RuntimeException("StorageService unreachable: " + e.getMessage(), e);
        }
    }

    private ServiceResponse delete(String path) {
        try {
            return client.delete(baseUrl + path);
        } catch (IOException e) {
            throw new RuntimeException("StorageService unreachable: " + e.getMessage(), e);
        }
    }

    private static String extractError(ServiceResponse resp) {
        try {
            Map map = resp.bodyAs(Map.class);
            return (String) map.get("error");
        } catch (Exception e) {
            return resp.body();
        }
    }
}
