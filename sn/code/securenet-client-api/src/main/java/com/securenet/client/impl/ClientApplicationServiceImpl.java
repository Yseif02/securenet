package com.securenet.client.impl;

import com.securenet.client.ClientApplicationService;
import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.model.*;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the SecureNet Client Application.
 *
 * <p>Simulates what the mobile/web app does: authenticates with the
 * platform, sends commands, queries events, and manages device state.
 * All requests go through the API Gateway with a bearer token in the
 * Authorization header.
 */
public class ClientApplicationServiceImpl implements ClientApplicationService {

    private final String gatewayUrl;
    private final String umsDirectUrl;
    private final ServiceClient httpClient;

    private volatile String bearerToken;
    private volatile String userId;
    private volatile List<Device> cachedDevices = List.of();
    private volatile boolean cloudAvailable = true;

    /**
     * @param gatewayUrl    base URL of the API Gateway (e.g. "http://localhost:8443")
     * @param umsDirectUrl  direct URL to UMS for login/register (before we have a token)
     */
    public ClientApplicationServiceImpl(String gatewayUrl, String umsDirectUrl) {
        this.gatewayUrl = Objects.requireNonNull(gatewayUrl, "gatewayUrl");
        this.umsDirectUrl = Objects.requireNonNull(umsDirectUrl, "umsDirectUrl");
        this.httpClient = new ServiceClient();
    }

    // =====================================================================
    // Session management
    // =====================================================================

    @Override
    public void login(String email, String rawPassword) throws AuthenticationException {
        try {
            ServiceResponse resp = httpClient.post(
                    umsDirectUrl + "/ums/login",
                    Map.of("email", email, "password", rawPassword));

            if (resp.statusCode() == 401) {
                throw new AuthenticationException("Invalid credentials");
            }
            if (!resp.isSuccess()) {
                throw new AuthenticationException("Login failed: " + resp.body());
            }

            Map result = JsonUtil.fromJson(resp.body(), Map.class);
            bearerToken = (String) result.get("tokenValue");
            userId = (String) result.get("userId");
            cloudAvailable = true;

            System.out.println("[Client] Logged in as " + email + " userId=" + userId);
        } catch (IOException e) {
            throw new AuthenticationException("Cannot reach platform: " + e.getMessage());
        }
    }

    @Override
    public void logout() {
        if (bearerToken == null) return;
        try {
            httpClient.post(umsDirectUrl + "/ums/revoke-token",
                    Map.of("tokenValue", bearerToken,
                            "userId", userId,
                            "issuedAt", Instant.now().toString(),
                            "expiresAt", Instant.now().toString()));
        } catch (IOException e) {
            System.err.println("[Client] Logout failed: " + e.getMessage());
        }
        System.out.println("[Client] Logged out");
        bearerToken = null;
        userId = null;
        cachedDevices = List.of();
    }

    @Override
    public void registerPushToken(String pushToken) {
        requireLoggedIn();
        try {
            apiPost("/notification/notify/register-token",
                    Map.of("userId", userId, "pushToken", pushToken, "platform", "FCM"));
            System.out.println("[Client] Push token registered");
        } catch (Exception e) {
            System.err.println("[Client] Push token registration failed: " + e.getMessage());
        }
    }

    // =====================================================================
    // Device dashboard
    // =====================================================================

    @Override
    public List<Device> loadDashboard() {
        requireLoggedIn();
        try {
            ServiceResponse resp = apiGet(
                    "/device-management/dms/devices/list?ownerId=" + userId);
            if (resp.isSuccess()) {
                cachedDevices = JsonUtil.gson().fromJson(resp.body(),
                        new TypeToken<List<Device>>(){}.getType());
                cloudAvailable = true;
                System.out.println("[Client] Dashboard: " + cachedDevices.size() + " devices");
            }
        } catch (Exception e) {
            System.err.println("[Client] Dashboard failed, using cache: " + e.getMessage());
            cloudAvailable = false;
        }
        return cachedDevices;
    }

    @Override
    public void startDeviceOnboarding(String qrPayloadOrDeviceId, String deviceType)
            throws IllegalArgumentException {
        requireLoggedIn();
        try {
            ServiceResponse resp = apiPost(
                    "/device-management/dms/devices/register",
                    Map.of("ownerId", userId,
                            "deviceType", deviceType,
                            "qrPayload", qrPayloadOrDeviceId));
            if (resp.isSuccess()) {
                Device device = JsonUtil.fromJson(resp.body(), Device.class);
                System.out.println("[Client] Onboarding started: " + device.deviceId());
            } else {
                System.err.println("[Client] Onboarding failed: " + resp.body());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Onboarding failed: " + e.getMessage());
        }
    }

    @Override
    public void sendLockCommand(String deviceId) throws DeviceOfflineException {
        requireLoggedIn();
        try {
            ServiceResponse resp = apiPost(
                    "/device-management/dms/devices/lock",
                    Map.of("deviceId", deviceId));
            if (resp.statusCode() == 409) throw new DeviceOfflineException(deviceId);
            Map result = JsonUtil.fromJson(resp.body(), Map.class);
            System.out.println("[Client] LOCK " + deviceId +
                    " → acknowledged=" + result.get("acknowledged"));
        } catch (DeviceOfflineException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[Client] Lock failed: " + e.getMessage());
        }
    }

    @Override
    public void sendUnlockCommand(String deviceId) throws DeviceOfflineException {
        requireLoggedIn();
        try {
            ServiceResponse resp = apiPost(
                    "/device-management/dms/devices/unlock",
                    Map.of("deviceId", deviceId));
            if (resp.statusCode() == 409) throw new DeviceOfflineException(deviceId);
            Map result = JsonUtil.fromJson(resp.body(), Map.class);
            System.out.println("[Client] UNLOCK " + deviceId +
                    " → acknowledged=" + result.get("acknowledged"));
        } catch (DeviceOfflineException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[Client] Unlock failed: " + e.getMessage());
        }
    }

    // =====================================================================
    // Event timeline
    // =====================================================================

    @Override
    public List<EventSummary> loadEventTimeline(String deviceId, Instant from,
                                                Instant to, int maxEvents) {
        requireLoggedIn();
        try {
            ServiceResponse resp = apiGet(
                    "/event-processing/eps/events/device/" + deviceId +
                            "?from=" + from + "&to=" + to + "&max=" + maxEvents);
            if (resp.isSuccess()) {
                List<SecurityEvent> events = JsonUtil.gson().fromJson(resp.body(),
                        new TypeToken<List<SecurityEvent>>(){}.getType());
                List<EventSummary> summaries = events.stream()
                        .map(EventSummary::from)
                        .collect(Collectors.toUnmodifiableList());
                System.out.println("[Client] Timeline " + deviceId + ": " +
                        summaries.size() + " events");
                return summaries;
            }
        } catch (Exception e) {
            System.err.println("[Client] Timeline failed: " + e.getMessage());
        }
        return List.of();
    }

    // =====================================================================
    // Video
    // =====================================================================

    @Override
    public void startLiveStream(String deviceId)
            throws DeviceOfflineException, CloudUnavailableException {
        requireLoggedIn();
        if (!cloudAvailable) throw new CloudUnavailableException();
        try {
            ServiceResponse resp = apiPost(
                    "/device-management/dms/devices/stream-start",
                    Map.of("deviceId", deviceId,
                            "streamTargetUrl", "http://localhost:9005/vss/chunks/ingest"));
            if (resp.statusCode() == 409) throw new DeviceOfflineException(deviceId);
            System.out.println("[Client] Stream started: " + deviceId);
        } catch (DeviceOfflineException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[Client] Stream failed: " + e.getMessage());
        }
    }

    @Override
    public void stopLiveStream(String streamSessionId) {
        System.out.println("[Client] Stream stopped: " + streamSessionId);
    }

    @Override
    public void openVideoPlayback(String deviceId, Instant from, Instant to)
            throws VideoNotFoundException {
        requireLoggedIn();
        try {
            ServiceResponse resp = apiGet(
                    "/video-streaming/vss/clips/device/" + deviceId +
                            "?from=" + from + "&to=" + to);
            if (resp.isSuccess()) {
                List clips = JsonUtil.fromJson(resp.body(), List.class);
                if (clips.isEmpty()) throw new VideoNotFoundException("No footage");
                System.out.println("[Client] Playback: " + clips.size() + " clips");
            }
        } catch (VideoNotFoundException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[Client] Playback failed: " + e.getMessage());
        }
    }

    @Override
    public void onBandwidthChange(String streamSessionId, int bandwidthKbps) {
        String tier = bandwidthKbps >= 2000 ? "HD" : bandwidthKbps >= 500 ? "SD" : "LOW";
        System.out.println("[Client] Bandwidth: " + bandwidthKbps + "kbps → " + tier);
    }

    @Override
    public void onCloudConnectionLost() {
        cloudAvailable = false;
        System.out.println("[Client] Cloud lost — cached mode");
    }

    @Override
    public void onCloudConnectionRestored() {
        cloudAvailable = true;
        System.out.println("[Client] Cloud restored — re-syncing");
        loadDashboard();
    }

    // =====================================================================
    // Accessors
    // =====================================================================

    public String getBearerToken() { return bearerToken; }
    public String getUserId() { return userId; }
    public boolean isLoggedIn() { return bearerToken != null; }
    public boolean isCloudAvailable() { return cloudAvailable; }

    // =====================================================================
    // HTTP helpers — all go through API Gateway with auth header
    // =====================================================================

    private ServiceResponse apiGet(String path) throws IOException {
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(gatewayUrl + "/api" + path))
                .GET()
                .header("Authorization", "Bearer " + bearerToken)
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
        try {
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return new ServiceResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private ServiceResponse apiPost(String path, Object body) throws IOException {
        String json = JsonUtil.toJson(body);
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(gatewayUrl + "/api" + path))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
        try {
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return new ServiceResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private void requireLoggedIn() {
        if (bearerToken == null)
            throw new IllegalStateException("Not logged in — call login() first");
    }
}