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

    private static void section(String title) {
        System.out.println();
        System.out.println("----------------------------------------------------------------");
        System.out.println(" " + title);
        System.out.println("----------------------------------------------------------------");
    }

    private static void step(String message) {
        System.out.println("  " + message);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================");
        System.out.println("  SecureNet Platform — End-to-End Demo");
        System.out.println("  Walkthrough: user onboarding, device onboarding, MQTT runtime,");
        System.out.println("  commands, alerts, event timeline, and video playback.");
        System.out.println("================================================================");
        System.out.println("  Prerequisite: ./scripts/start-platform.sh is already running");
        System.out.println();

        ServiceClient rawClient = new ServiceClient();

        // 1. Create homeowner account
        section("1. Creating homeowner account");
        String email = "demo-" + System.currentTimeMillis() + "@example.com";
        ServiceResponse regResp = rawClient.post(UMS + "/ums/register",
                Map.of("email",       email,
                        "displayName", "Demo Homeowner",
                        "password",    "securenet123"));
        if (!regResp.isSuccess()) {
            System.err.println("Registration failed: " + regResp.body());
            return;
        }
        step("Registered homeowner: " + email);

        // 2. Seed firmware
        section("2. Seeding firmware");
        StorageGateway storage = new StorageGateway(STORAGE);
        for (DeviceType dt : DeviceType.values()) {
            try {
                storage.saveFirmwareBinary(dt.name(), "1.0.0", "SECURENET_FW".getBytes());
                step("Seeded firmware " + dt.name() + " v1.0.0");
            } catch (Exception e) {
                step("Firmware already present for " + dt.name());
            }
        }

        // 3. Login via Client API
        section("3. Logging in through the client API");
        ClientApplicationServiceImpl client = new ClientApplicationServiceImpl(GATEWAY, UMS);
        client.login(email, "securenet123");

        // 4. Register push token
        section("4. Registering a push token");
        client.registerPushToken("fcm-token-" + System.currentTimeMillis());

        // 5. Onboard devices
        section("5. Creating onboarding records");
        String suffix = String.valueOf(System.currentTimeMillis() % 10000);
        record Seed(String id, String token, DeviceType type) {}
        List<Seed> seeds = List.of(
                new Seed("sensor-" + suffix, "st-" + suffix, DeviceType.MOTION_SENSOR),
                new Seed("camera-" + suffix, "ct-" + suffix, DeviceType.CAMERA),
                new Seed("lock-"   + suffix, "lt-" + suffix, DeviceType.SMART_LOCK));

        for (Seed s : seeds) {
            client.startDeviceOnboarding(s.id() + ":" + s.token(), s.type().name());
            step("Prepared onboarding for " + s.type().name() + " -> " + s.id());
        }

        // 6. Dashboard (devices pending)
        section("6. Dashboard before devices boot");
        client.loadDashboard();

        // 7. Launch mock devices
        section("7. Launching mock devices");
        MockSensor sensor = new MockSensor(seeds.get(0).id(), seeds.get(0).token(), IDFS);
        MockCamera camera = new MockCamera(seeds.get(1).id(), seeds.get(1).token(), IDFS);
        MockLock   lock   = new MockLock(seeds.get(2).id(),   seeds.get(2).token(), IDFS);

        for (AbstractMockDevice d : List.of(sensor, camera, lock)) {
            new Thread(d, "Mock-" + d.getDeviceId()).start();
            step("Started " + d.getClass().getSimpleName() + " -> " + d.getDeviceId());
            Thread.sleep(200);
        }
        step("Waiting for bootstrap + provisioning to settle (6s)");
        Thread.sleep(6000);

        // 8. Dashboard (devices online)
        section("8. Dashboard after devices come online");
        for (Device d : client.loadDashboard()) {
            step(d.deviceId() + " [" + d.type() + "] " + d.status());
        }

        // 9. Commands via Client API → Gateway → DMS → IDFS → MQTT → Device
        // startLiveStream: DMS opens VSS session → STREAM_START → MockCamera
        // sends chunks → DMS auto-closes after 10s → clip archived to PostgreSQL
        section("9. Sending commands through the full stack");
        step("Locking door");
        client.sendLockCommand(seeds.get(2).id());
        Thread.sleep(1000);
        step("Unlocking door");
        client.sendUnlockCommand(seeds.get(2).id());
        Thread.sleep(1000);
        step("Starting live stream on camera");
        Instant streamStarted = Instant.now();
        client.startLiveStream(seeds.get(1).id());

        // 10. Event timeline
        section("10. Reading the event timeline");
        Thread.sleep(5000);
        Instant now = Instant.now();
        for (Seed s : seeds) {
            List<EventSummary> events = client.loadEventTimeline(
                    s.id(), now.minus(1, ChronoUnit.HOURS), now, 10);
            step(s.id() + ": " + events.size() + " event(s)");
            for (EventSummary e : events) {
                step("  " + e.type() + " at " + e.occurredAt());
            }
        }

        // 10b. Video playback — poll until a clip appears or timeout
        section("10b. Polling for archived video playback");
        step("Polling for archived clip (up to "
                + (CLIP_POLL_TIMEOUT_MS / 1000) + "s)");

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
                step("Clip archived: " + clipId);
                break;
            }

            step("No clip yet, retrying in "
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
                step("Signed playback URL: " + playbackUrl);
            } else {
                step("Playback URL generation failed: " + urlResp.body());
            }
        } else {
            step("No clip found within timeout — stream may not have closed yet");
        }

        // 11. Raft + Cluster Manager status
        section("11. Inspecting cluster status");
        for (int p : List.of(9013, 9023, 9033)) {
            try {
                step("Raft " + p + ": "
                        + rawClient.get("http://localhost:" + p + "/raft/status").body());
            } catch (Exception e) {
                step("Raft " + p + ": unreachable");
            }
        }
        try {
            String clusterStatus = rawClient.get(
                    "http://localhost:9090/cluster/status").body();
            step("Cluster Manager: "
                    + clusterStatus.substring(0, Math.min(200, clusterStatus.length()))
                    + "...");
        } catch (Exception e) {
            step("Cluster Manager: unreachable");
        }

        // 12. Logout — only reached after video playback completes
        section("12. Logging out");
        client.logout();

        System.out.println("\n================================================================");
        System.out.println("  Demo complete");
        System.out.println("  Mock devices are still running in the background.");
        System.out.println("  Press Ctrl+C to stop them and end the demo cleanly.");
        System.out.println("================================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sensor.shutdown(); camera.shutdown(); lock.shutdown();
            System.out.println("Mock devices stopped.");
        }));
        Thread.currentThread().join();
    }
}
