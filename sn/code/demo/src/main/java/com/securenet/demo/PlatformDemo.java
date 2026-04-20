package com.securenet.demo;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.client.impl.ClientApplicationServiceImpl;
import com.securenet.model.Device;
import com.securenet.model.DeviceType;
import com.securenet.model.EventSummary;
import com.securenet.storage.StorageGateway;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * End-to-end integration demo that exercises the full SecureNet platform
 * through the Client API — the same interface a real mobile/web app uses.
 *
 * <p>Assumes platform is running via {@code ./scripts/start-platform.sh}.
 *
 * <p>Usage:
 * <pre>
 * java -cp "$CLASSPATH" com.securenet.demo.PlatformDemo
 * </pre>
 */
public class PlatformDemo {

    private static final String GATEWAY = "http://localhost:8443";
    private static final String UMS     = "http://localhost:9001";
    private static final String STORAGE = "http://localhost:9000";
    private static final String IDFS    = "http://localhost:8080";
    private static final String VSS     = "http://localhost:9005";

    /** How long to poll for a clip before giving up (ms). */
    private static final long CLIP_POLL_TIMEOUT_MS  = 60_000;
    /** How often to poll (ms). */
    private static final long CLIP_POLL_INTERVAL_MS = 2_000;

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================");
        System.out.println("  SecureNet Platform — End-to-End Demo");
        System.out.println("  All requests route through API Gateway + Client API");
        System.out.println("================================================================");
        System.out.println();

        ServiceClient rawClient = new ServiceClient();

        // 1. Create homeowner account
        System.out.println("--- 1. Creating homeowner account ---");
        String email = "demo-" + System.currentTimeMillis() + "@example.com";
        ServiceResponse regResp = rawClient.post(UMS + "/ums/register",
                Map.of("email",       email,
                        "displayName", "Demo Homeowner",
                        "password",    "securenet123"));
        if (!regResp.isSuccess()) {
            System.err.println("Registration failed: " + regResp.body());
            return;
        }
        System.out.println("  Registered: " + email);

        // 2. Seed firmware
        System.out.println("\n--- 2. Seeding firmware ---");
        StorageGateway storage = new StorageGateway(STORAGE);
        for (DeviceType dt : DeviceType.values()) {
            try {
                storage.saveFirmwareBinary(dt.name(), "1.0.0", "SECURENET_FW".getBytes());
                System.out.println("  " + dt.name() + " v1.0.0 ✓");
            } catch (Exception e) {
                System.out.println("  " + dt.name() + " already seeded");
            }
        }

        // 3. Login via Client API
        System.out.println("\n--- 3. Logging in ---");
        ClientApplicationServiceImpl client = new ClientApplicationServiceImpl(GATEWAY, UMS);
        client.login(email, "securenet123");

        // 4. Register push token
        System.out.println("\n--- 4. Push token ---");
        client.registerPushToken("fcm-token-" + System.currentTimeMillis());

        // 5. Onboard devices
        System.out.println("\n--- 5. Onboarding devices ---");
        String suffix = String.valueOf(System.currentTimeMillis() % 10000);
        record Seed(String id, String token, DeviceType type) {}
        List<Seed> seeds = List.of(
                new Seed("sensor-" + suffix, "st-" + suffix, DeviceType.MOTION_SENSOR),
                new Seed("camera-" + suffix, "ct-" + suffix, DeviceType.CAMERA),
                new Seed("lock-"   + suffix, "lt-" + suffix, DeviceType.SMART_LOCK));

        for (Seed s : seeds) {
            client.startDeviceOnboarding(s.id() + ":" + s.token(), s.type().name());
        }

        // 6. Dashboard (devices pending)
        System.out.println("\n--- 6. Dashboard (pre-onboarding) ---");
        client.loadDashboard();

        // 7. Launch mock devices
        System.out.println("\n--- 7. Launching mock devices ---");
        MockSensor sensor = new MockSensor(seeds.get(0).id(), seeds.get(0).token(), IDFS);
        MockCamera camera = new MockCamera(seeds.get(1).id(), seeds.get(1).token(), IDFS);
        MockLock   lock   = new MockLock(seeds.get(2).id(),   seeds.get(2).token(), IDFS);

        for (AbstractMockDevice d : List.of(sensor, camera, lock)) {
            new Thread(d, "Mock-" + d.getDeviceId()).start();
            System.out.println("  " + d.getClass().getSimpleName() + ": " + d.getDeviceId());
            Thread.sleep(200);
        }
        System.out.println("  Waiting for onboarding (6s)...");
        Thread.sleep(6000);

        // 8. Dashboard (devices online)
        System.out.println("\n--- 8. Dashboard (post-onboarding) ---");
        for (Device d : client.loadDashboard()) {
            System.out.println("  " + d.deviceId() + " [" + d.type() + "] " + d.status());
        }

        // 9. Commands via Client API → Gateway → DMS → IDFS → MQTT → Device
        // startLiveStream: DMS opens VSS session → STREAM_START → MockCamera
        // sends chunks → DMS auto-closes after 10s → clip archived to PostgreSQL
        System.out.println("\n--- 9. Commands ---");
        client.sendLockCommand(seeds.get(2).id());
        Thread.sleep(1000);
        client.sendUnlockCommand(seeds.get(2).id());
        Thread.sleep(1000);
        Instant streamStarted = Instant.now();
        client.startLiveStream(seeds.get(1).id());

        // 10. Event timeline
        System.out.println("\n--- 10. Event timeline ---");
        Thread.sleep(5000);
        Instant now = Instant.now();
        for (Seed s : seeds) {
            List<EventSummary> events = client.loadEventTimeline(
                    s.id(), now.minus(1, ChronoUnit.HOURS), now, 10);
            System.out.println("  " + s.id() + ": " + events.size() + " event(s)");
            for (EventSummary e : events) {
                System.out.println("    " + e.type() + " at " + e.occurredAt());
            }
        }

        // 10b. Video playback — poll until a clip appears or timeout
        System.out.println("\n--- 10b. Video playback ---");
        System.out.println("  Polling for archived clip (up to "
                + (CLIP_POLL_TIMEOUT_MS / 1000) + "s)...");

        String cameraId  = seeds.get(1).id();
        Instant queryFrom = streamStarted.minus(1, ChronoUnit.MINUTES);
        String clipId    = null;
        long pollDeadline = System.currentTimeMillis() + CLIP_POLL_TIMEOUT_MS;

        while (System.currentTimeMillis() < pollDeadline) {
            ServiceResponse clipsResp = rawClient.get(
                    VSS + "/vss/clips/device/" + cameraId
                            + "?from=" + queryFrom + "&to=" + Instant.now());

            if (clipsResp.isSuccess() && clipsResp.body().contains("clipId")) {
                String body  = clipsResp.body();
                int startIdx = body.indexOf("\"clipId\":\"") + 10;
                clipId       = body.substring(startIdx, body.indexOf("\"", startIdx));
                System.out.println("  ✓ Clip archived: " + clipId);
                break;
            }

            System.out.println("  No clip yet, retrying in "
                    + (CLIP_POLL_INTERVAL_MS / 1000) + "s...");
            Thread.sleep(CLIP_POLL_INTERVAL_MS);
        }

        if (clipId != null) {
            ServiceResponse urlResp = rawClient.get(
                    VSS + "/vss/clips/playback?clipId=" + clipId + "&validFor=3600");
            if (urlResp.isSuccess() && urlResp.body().contains("playbackUrl")) {
                String urlBody     = urlResp.body();
                int urlStart       = urlBody.indexOf("\"playbackUrl\":\"") + 15;
                String playbackUrl = urlBody.substring(urlStart,
                        urlBody.indexOf("\"", urlStart));
                System.out.println("  ✓ Signed playback URL: " + playbackUrl);
            } else {
                System.out.println("  Playback URL generation failed: " + urlResp.body());
            }
        } else {
            System.out.println("  No clip found within timeout — stream may not have closed yet");
        }

        // 11. Raft + Cluster Manager status
        System.out.println("\n--- 11. Cluster status ---");
        for (int p : List.of(9013, 9023, 9033)) {
            try {
                System.out.println("  Raft " + p + ": "
                        + rawClient.get("http://localhost:" + p + "/raft/status").body());
            } catch (Exception e) {
                System.out.println("  Raft " + p + ": unreachable");
            }
        }
        try {
            String clusterStatus = rawClient.get(
                    "http://localhost:9090/cluster/status").body();
            System.out.println("  Cluster Manager: "
                    + clusterStatus.substring(0, Math.min(200, clusterStatus.length()))
                    + "...");
        } catch (Exception e) {
            System.out.println("  Cluster Manager: unreachable");
        }

        // 12. Logout — only reached after video playback completes
        System.out.println("\n--- 12. Logout ---");
        client.logout();

        System.out.println("\n================================================================");
        System.out.println("  Demo complete — mock devices running in background");
        System.out.println("  Press Ctrl+C to stop");
        System.out.println("================================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sensor.shutdown(); camera.shutdown(); lock.shutdown();
            System.out.println("Mock devices stopped.");
        }));
        Thread.currentThread().join();
    }
}