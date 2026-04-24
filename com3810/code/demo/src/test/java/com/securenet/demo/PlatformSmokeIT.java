package com.securenet.demo;

import com.google.gson.reflect.TypeToken;
import com.securenet.client.impl.ClientApplicationServiceImpl;
import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.model.Device;
import com.securenet.model.DeviceType;
import com.securenet.storage.StorageGateway;
import com.securenet.testsupport.Eventually;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformSmokeIT {

    private static final String GATEWAY = "http://localhost:8443";
    private static final String UMS = "http://localhost:9001";
    private static final String STORAGE = "http://localhost:9000";
    private static final String IDFS = "http://localhost:8080";
    private static final String VSS = "http://localhost:9005";

    @Test
    void fullPlatformSmokeFlowProducesEventsAndArchivedClip() throws Exception {
        assertTrue(isHealthy(GATEWAY), "API gateway is not reachable at " + GATEWAY);
        assertTrue(isHealthy(UMS), "UMS is not reachable at " + UMS);
        assertTrue(isHealthy(VSS), "VSS is not reachable at " + VSS);

        ServiceClient rawClient = new ServiceClient();
        StorageGateway storageGateway = new StorageGateway(STORAGE);

        for (DeviceType type : DeviceType.values()) {
            try {
                storageGateway.saveFirmwareBinary(type.name(), "1.0.0", "SECURENET_FW".getBytes());
            } catch (Exception ignored) {
            }
        }

        String suffix = "smoke-" + System.currentTimeMillis();
        String email = suffix + "@example.com";
        var registration = rawClient.post(UMS + "/ums/register", Map.of(
                "email", email,
                "displayName", "Smoke Test",
                "password", "securenet123"
        ));
        assertTrue(registration.isSuccess(), "Registration failed: " + registration.body());

        ClientApplicationServiceImpl client = new ClientApplicationServiceImpl(GATEWAY, UMS);
        client.login(email, "securenet123");
        client.registerPushToken("smoke-fcm-" + suffix);

        String sensorId = "sensor-" + suffix;
        String cameraId = "camera-" + suffix;
        String lockId = "lock-" + suffix;

        client.startDeviceOnboarding(sensorId + ":st-" + suffix, DeviceType.MOTION_SENSOR.name());
        client.startDeviceOnboarding(cameraId + ":ct-" + suffix, DeviceType.CAMERA.name());
        client.startDeviceOnboarding(lockId + ":lt-" + suffix, DeviceType.SMART_LOCK.name());

        MockSensor sensor = new MockSensor(sensorId, "st-" + suffix, IDFS);
        MockCamera camera = new MockCamera(cameraId, "ct-" + suffix, IDFS);
        MockLock lock = new MockLock(lockId, "lt-" + suffix, IDFS);

        Thread sensorThread = new Thread(sensor, "smoke-sensor");
        Thread cameraThread = new Thread(camera, "smoke-camera");
        Thread lockThread = new Thread(lock, "smoke-lock");
        sensorThread.setDaemon(true);
        cameraThread.setDaemon(true);
        lockThread.setDaemon(true);
        sensorThread.start();
        cameraThread.start();
        lockThread.start();

        Instant streamStarted = Instant.now();
        try {
            Eventually.await("all smoke devices online",
                    Duration.ofSeconds(20),
                    Duration.ofMillis(500),
                    () -> client.loadDashboard().stream()
                            .filter(d -> d.deviceId().endsWith(suffix))
                            .count() == 3
                            && client.loadDashboard().stream()
                            .filter(d -> d.deviceId().endsWith(suffix))
                            .allMatch(d -> d.status().name().equals("ONLINE")));

            client.sendLockCommand(lockId);
            client.sendUnlockCommand(lockId);
            client.startLiveStream(cameraId);

            Eventually.await("archived clip",
                    Duration.ofSeconds(80),
                    Duration.ofSeconds(2),
                    () -> {
                        var resp = rawClient.get(VSS + "/vss/clips/device/" + cameraId
                                + "?from=" + streamStarted.minus(1, ChronoUnit.MINUTES)
                                + "&to=" + Instant.now());
                        if (!resp.isSuccess()) return false;
                        List<Map<String, Object>> clips = JsonUtil.gson().fromJson(resp.body(),
                                new TypeToken<List<Map<String, Object>>>(){}.getType());
                        return clips.stream().anyMatch(clip ->
                                ((Number) clip.getOrDefault("fileSizeBytes", 0)).longValue() > 0);
                    });

            assertFalse(hasExternalPahoDirectory(sensorId, cameraId, lockId),
                    "Found leaked mock-device persistence outside the run-owned device-records directory");
        } finally {
            sensor.shutdown();
            camera.shutdown();
            lock.shutdown();
            client.logout();
        }
    }

    private static boolean isHealthy(String baseUrl) {
        try {
            return new ServiceClient().get(baseUrl + "/health").isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasExternalPahoDirectory(String... deviceIds) throws Exception {
        Path projectRoot = findRepoRoot();
        Path allowedRoot = projectRoot.resolve("logs").resolve("latest").resolve("device-records").normalize();
        try (var paths = Files.walk(projectRoot, 2)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("securenet-device-"))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        for (String deviceId : deviceIds) {
                            if (name.contains(deviceId)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .anyMatch(path -> !path.normalize().startsWith(allowedRoot));
        }
    }

    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("scripts").resolve("start-platform.sh"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of("").toAbsolutePath().normalize();
    }
}
