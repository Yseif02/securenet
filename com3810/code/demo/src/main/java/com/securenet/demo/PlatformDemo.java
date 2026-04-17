package com.securenet.demo;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.model.DeviceType;
import com.securenet.storage.StorageGateway;

import java.util.List;
import java.util.Map;

/**
 * Integration test harness that runs against an already-running
 * SecureNet platform (started by {@code scripts/start-platform.sh}).
 *
 * <p>This class does NOT start any services. It assumes the full
 * platform is already running and:
 * <ol>
 *   <li>Seeds a homeowner account and firmware binaries</li>
 *   <li>Registers 3 pending devices via DMS</li>
 *   <li>Launches mock devices that onboard, provision, and enter steady state</li>
 *   <li>Sends test commands (LOCK, UNLOCK, STREAM_START)</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * # Start the platform first:
 * ./scripts/start-platform.sh
 *
 * # Then run this demo:
 * java -cp ... com.securenet.demo.PlatformDemo
 * </pre>
 */
public class PlatformDemo {

    private static final String UMS = "http://localhost:9001";
    private static final String DMS = "http://localhost:9002";
    private static final String STORAGE = "http://localhost:9000";
    private static final String IDFS = "http://localhost:8080";

    public static void main(String[] args) throws Exception {
        System.out.println("=== SecureNet Platform Demo ===");
        System.out.println("(Assumes platform is already running via start-platform.sh)");
        System.out.println();

        ServiceClient client = new ServiceClient();

        // ---- 1. Seed homeowner ----
        System.out.println("--- Seeding homeowner ---");
        ServiceResponse regResp = client.post(UMS + "/ums/register",
                Map.of("email", "demo@example.com",
                        "displayName", "Demo Homeowner",
                        "password", "securenet123"));

        Map userMap = JsonUtil.fromJson(regResp.body(), Map.class);
        String ownerId = (String) userMap.get("userId");

        if (ownerId == null) {
            System.out.println("  User already exists, logging in...");
            ServiceResponse loginResp = client.post(UMS + "/ums/login",
                    Map.of("email", "demo@example.com", "password", "securenet123"));
            Map loginMap = JsonUtil.fromJson(loginResp.body(), Map.class);
            ownerId = (String) loginMap.get("userId");
        }
        System.out.println("  Owner ID: " + ownerId);

        // ---- 2. Seed firmware ----
        System.out.println("--- Seeding firmware ---");
        StorageGateway storageGateway = new StorageGateway(STORAGE);
        byte[] fakeFw = "SECURENET_FW_V1".getBytes();
        for (DeviceType dt : DeviceType.values()) {
            try {
                storageGateway.saveFirmwareBinary(dt.name(), "1.0.0", fakeFw);
                System.out.println("  Seeded: " + dt.name() + " v1.0.0");
            } catch (Exception e) {
                System.out.println("  " + dt.name() + " already seeded");
            }
        }

        // ---- 3. Register pending devices ----
        System.out.println("--- Registering devices ---");
        record Seed(String id, String token, DeviceType type) {}
        List<Seed> seeds = List.of(
                new Seed("demo-sensor-001", "st-001", DeviceType.MOTION_SENSOR),
                new Seed("demo-camera-001", "ct-001", DeviceType.CAMERA),
                new Seed("demo-lock-001", "lt-001", DeviceType.SMART_LOCK));

        for (Seed s : seeds) {
            ServiceResponse resp = client.post(DMS + "/dms/devices/register",
                    Map.of("ownerId", ownerId,
                            "deviceType", s.type().name(),
                            "qrPayload", s.id() + ":" + s.token()));
            System.out.println("  " + s.id() + " → HTTP " + resp.statusCode());
        }

        // ---- 4. Launch mock devices ----
        System.out.println();
        System.out.println("--- Launching mock devices ---");
        MockSensor sensor = new MockSensor("demo-sensor-001", "st-001", IDFS);
        MockCamera camera = new MockCamera("demo-camera-001", "ct-001", IDFS);
        MockLock lock = new MockLock("demo-lock-001", "lt-001", IDFS);

        for (AbstractMockDevice device : List.of(sensor, camera, lock)) {
            Thread t = new Thread(device, "Mock-" + device.getDeviceId());
            t.setDaemon(true);
            t.start();
            System.out.println("  Launched " + device.getClass().getSimpleName() +
                    ": " + device.getDeviceId());
            Thread.sleep(200);
        }

        System.out.println("  Waiting for onboarding to complete...");
        Thread.sleep(6000);

        // ---- 5. Test commands ----
        System.out.println();
        System.out.println("--- Testing commands ---");

        System.out.println("  LOCK demo-lock-001...");
        ServiceResponse lockResp = client.post(DMS + "/dms/devices/lock",
                Map.of("deviceId", "demo-lock-001"));
        System.out.println("    Result: HTTP " + lockResp.statusCode() + " " + lockResp.body());
        Thread.sleep(500);

        System.out.println("  UNLOCK demo-lock-001...");
        ServiceResponse unlockResp = client.post(DMS + "/dms/devices/unlock",
                Map.of("deviceId", "demo-lock-001"));
        System.out.println("    Result: HTTP " + unlockResp.statusCode() + " " + unlockResp.body());
        Thread.sleep(500);

        System.out.println("  STREAM_START demo-camera-001...");
        ServiceResponse streamResp = client.post(DMS + "/dms/devices/stream-start",
                Map.of("deviceId", "demo-camera-001",
                        "streamTargetUrl", "http://localhost:9005/vss/chunks/ingest"));
        System.out.println("    Result: HTTP " + streamResp.statusCode() + " " + streamResp.body());

        // ---- 6. Query events via EPS ----
        Thread.sleep(3000);
        System.out.println();
        System.out.println("--- Querying events from EPS ---");

        // Try each EPS node to find the leader
        for (int epsPort : List.of(9003, 9103, 9203)) {
            try {
                ServiceResponse eventsResp = client.get(
                        "http://localhost:" + epsPort +
                                "/eps/events/device/demo-sensor-001?max=10");
                if (eventsResp.isSuccess()) {
                    System.out.println("  Events from EPS on port " + epsPort + ":");
                    System.out.println("    " + eventsResp.body().substring(0,
                            Math.min(200, eventsResp.body().length())) + "...");
                    break;
                }
            } catch (Exception e) {
                System.out.println("  EPS " + epsPort + ": " + e.getMessage());
            }
        }

        // ---- 7. Check Raft status ----
        System.out.println();
        System.out.println("--- Raft cluster status ---");
        for (int raftPort : List.of(9013, 9023, 9033)) {
            try {
                ServiceResponse status = client.get(
                        "http://localhost:" + raftPort + "/raft/status");
                System.out.println("  " + raftPort + ": " + status.body());
            } catch (Exception e) {
                System.out.println("  " + raftPort + ": unreachable");
            }
        }

        System.out.println();
        System.out.println("=== Demo running — devices sending heartbeats ===");
        System.out.println("Press Ctrl+C to stop mock devices.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sensor.shutdown();
            camera.shutdown();
            lock.shutdown();
            System.out.println("Mock devices stopped.");
        }));

        Thread.currentThread().join();
    }
}
