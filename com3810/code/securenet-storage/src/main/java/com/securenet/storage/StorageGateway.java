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
