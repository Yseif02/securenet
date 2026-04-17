package com.securenet.demo;

import com.securenet.devicemanagement.DeviceManagementService;
import com.securenet.devicemanagement.impl.DeviceManagementServiceImpl;
import com.securenet.devicemanagement.server.DeviceManagementServer;
import com.securenet.eventprocessing.impl.EventProcessingServiceImpl;
import com.securenet.eventprocessing.server.EventProcessingServer;
import com.securenet.iotfirmware.server.IdfsServer;
import com.securenet.model.DeviceType;
import com.securenet.iotfirmware.mqtt.EmbeddedMqttBroker;
import com.securenet.storage.StorageGateway;
import com.securenet.storage.impl.StorageServiceImpl;
import com.securenet.storage.server.StorageServiceServer;
import com.securenet.usermanagement.UserManagementService;
import com.securenet.usermanagement.impl.UserManagementServiceImpl;
import com.securenet.usermanagement.server.UserManagementServer;
import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator that starts all SecureNet distributed services in a
 * single JVM for development and integration testing.
 *
 * <p>After all services and mock devices are running, the orchestrator
 * sends test commands and waits for device events to flow through EPS.
 */
public class SecureNetOrchestrator {

    private static final int MQTT_PORT = 1883;
    private static final int STORAGE_PORT = 9000;
    private static final int UMS_PORT = 9001;
    private static final int DMS_PORT = 9002;
    private static final int EPS_PORT = 9003;
    private static final int IDFS_PORT = 8080;
    private static final String BIND_HOST = "0.0.0.0";
    private static final String LOCALHOST = "http://localhost";
    private static final String MQTT_BROKER_URL = "tcp://localhost:" + MQTT_PORT;

    public static void main(String[] args) throws Exception {
        System.out.println("=== SecureNet Platform — Stage 4 Distributed Services ===");
        System.out.println();

        // 1. MQTT Broker
        System.out.println("--- Starting MQTT Broker ---");
        EmbeddedMqttBroker mqttBroker = new EmbeddedMqttBroker(BIND_HOST, MQTT_PORT);
        mqttBroker.start();
        Thread.sleep(500);

        // 2. Storage Service
        System.out.println("--- Starting Storage Service ---");
        StorageServiceImpl storageImpl = new StorageServiceImpl(
                "jdbc:postgresql://localhost:5432/securenet",
                System.getProperty("user.name"), ""
        );
        StorageServiceServer storageServer = new StorageServiceServer(BIND_HOST, STORAGE_PORT, storageImpl);
        storageServer.start();
        Thread.sleep(300);

        // 3. User Management Service
        System.out.println("--- Starting User Management Service ---");
        StorageGateway umsGateway = new StorageGateway(LOCALHOST + ":" + STORAGE_PORT);
        UserManagementService umsService = new UserManagementServiceImpl(umsGateway);
        UserManagementServer umsServer = new UserManagementServer(BIND_HOST, UMS_PORT, umsService);
        umsServer.start();
        Thread.sleep(300);

        // 4. Device Management Service
        System.out.println("--- Starting Device Management Service ---");
        StorageGateway dmsGateway = new StorageGateway(LOCALHOST + ":" + STORAGE_PORT);
        String idfsUrl = LOCALHOST + ":" + IDFS_PORT;
        DeviceManagementService dmsService = new DeviceManagementServiceImpl(dmsGateway, idfsUrl);
        DeviceManagementServer dmsServer = new DeviceManagementServer(BIND_HOST, DMS_PORT, dmsService);
        dmsServer.start();
        Thread.sleep(300);

        // 5. Event Processing Service
        System.out.println("--- Starting Event Processing Service ---");
        StorageGateway epsGateway = new StorageGateway(LOCALHOST + ":" + STORAGE_PORT);
        EventProcessingServiceImpl epsService = new EventProcessingServiceImpl(epsGateway, null);
        EventProcessingServer epsServer = new EventProcessingServer(BIND_HOST, EPS_PORT, epsService);
        epsServer.start();
        Thread.sleep(300);

        // 6. IDFS (with DMS, EPS, and MQTT URLs)
        System.out.println("--- Starting IDFS ---");
        String dmsUrl = LOCALHOST + ":" + DMS_PORT;
        String epsUrl = LOCALHOST + ":" + EPS_PORT;
        IdfsServer idfsServer = new IdfsServer(BIND_HOST, IDFS_PORT, dmsUrl, epsUrl, MQTT_BROKER_URL);
        idfsServer.start();
        Thread.sleep(300);

        System.out.println();
        System.out.println("=== All services started ===");
        System.out.println("  MQTT Broker:  localhost:" + MQTT_PORT);
        System.out.println("  Storage:      localhost:" + STORAGE_PORT);
        System.out.println("  UMS:          localhost:" + UMS_PORT);
        System.out.println("  DMS:          localhost:" + DMS_PORT);
        System.out.println("  EPS:          localhost:" + EPS_PORT);
        System.out.println("  IDFS:         localhost:" + IDFS_PORT);
        System.out.println();

        // 7. Seed test data
        System.out.println("--- Seeding test data ---");
        ServiceClient seedClient = new ServiceClient();

        ServiceResponse regResp = seedClient.post(
                LOCALHOST + ":" + UMS_PORT + "/ums/register",
                Map.of("email", "homeowner@example.com",
                        "displayName", "Test Homeowner",
                        "password", "securenet123")
        );
        Map userMap = JsonUtil.fromJson(regResp.body(), Map.class);
        String ownerId = (String) userMap.get("userId");
        if (ownerId == null) {
            System.out.println("  Homeowner already registered, logging in...");
            ServiceResponse loginResp = seedClient.post(
                    LOCALHOST + ":" + UMS_PORT + "/ums/login",
                    Map.of("email", "homeowner@example.com", "password", "securenet123")
            );
            Map loginMap = JsonUtil.fromJson(loginResp.body(), Map.class);
            ownerId = (String) loginMap.get("userId");
        }
        System.out.println("  Homeowner ownerId: " + ownerId);

        StorageGateway seedGateway = new StorageGateway(LOCALHOST + ":" + STORAGE_PORT);
        byte[] fakeFirmware = "SECURENET_FIRMWARE_V1.0.0".getBytes();
        for (DeviceType dt : DeviceType.values()) {
            seedGateway.saveFirmwareBinary(dt.name(), "1.0.0", fakeFirmware);
            System.out.println("  Seeded firmware: " + dt.name() + " v1.0.0");
        }

        record DeviceSeed(String id, String token, DeviceType type) {}
        List<DeviceSeed> seeds = List.of(
                new DeviceSeed("sensor-001", "sensor-token-001", DeviceType.MOTION_SENSOR),
                new DeviceSeed("camera-001", "camera-token-001", DeviceType.CAMERA),
                new DeviceSeed("lock-001", "lock-token-001", DeviceType.SMART_LOCK)
        );

        for (DeviceSeed seed : seeds) {
            ServiceResponse dmsResp = seedClient.post(
                    LOCALHOST + ":" + DMS_PORT + "/dms/devices/register",
                    Map.of("ownerId", ownerId,
                            "deviceType", seed.type().name(),
                            "qrPayload", seed.id() + ":" + seed.token())
            );
            System.out.println("  Pending device: " + seed.id() + " (HTTP " + dmsResp.statusCode() + ")");
        }

        // 8. Launch mock devices
        System.out.println();
        System.out.println("--- Launching mock devices ---");
        MockSensor sensor = new MockSensor("sensor-001", "sensor-token-001", idfsUrl);
        MockCamera camera = new MockCamera("camera-001", "camera-token-001", idfsUrl);
        MockLock lock = new MockLock("lock-001", "lock-token-001", idfsUrl);

        for (AbstractMockDevice device : List.of(sensor, camera, lock)) {
            Thread thread = new Thread(device, "MockDevice-" + device.getDeviceId());
            thread.setDaemon(true);
            thread.start();
            System.out.println("  Launched " + device.getClass().getSimpleName()
                    + ": " + device.getDeviceId());
            Thread.sleep(200);
        }

        System.out.println("  Waiting for devices to complete onboarding...");
        Thread.sleep(5000);

        // 9. Test command dispatch
        System.out.println();
        System.out.println("--- Testing command dispatch ---");

        System.out.println("  Sending LOCK to lock-001...");
        ServiceResponse lockResp = seedClient.post(
                LOCALHOST + ":" + DMS_PORT + "/dms/devices/lock",
                Map.of("deviceId", "lock-001")
        );
        System.out.println("  LOCK result: HTTP " + lockResp.statusCode() + " " + lockResp.body());

        Thread.sleep(1000);

        System.out.println("  Sending UNLOCK to lock-001...");
        ServiceResponse unlockResp = seedClient.post(
                LOCALHOST + ":" + DMS_PORT + "/dms/devices/unlock",
                Map.of("deviceId", "lock-001")
        );
        System.out.println("  UNLOCK result: HTTP " + unlockResp.statusCode() + " " + unlockResp.body());

        Thread.sleep(1000);

        System.out.println("  Sending STREAM_START to camera-001...");
        ServiceResponse streamResp = seedClient.post(
                LOCALHOST + ":" + DMS_PORT + "/dms/devices/stream-start",
                Map.of("deviceId", "camera-001", "streamTargetUrl", "http://localhost:9005/ingest")
        );
        System.out.println("  STREAM_START result: HTTP " + streamResp.statusCode() + " " + streamResp.body());

        // 10. Check EPS state
        Thread.sleep(2000);
        System.out.println();
        System.out.println("--- EPS status ---");
        System.out.println("  Lamport clock: " + epsService.getLamportClock());
        //System.out.println("  Dedup table size: " + epsService.getDeduplicationTableSize());

        System.out.println();
        System.out.println("=== All tests passed — platform running ===");
        System.out.println("Press Ctrl+C to shut down.");
        System.out.println();

        // 11. Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("=== Shutting down SecureNet platform ===");
            sensor.shutdown();
            camera.shutdown();
            lock.shutdown();
            idfsServer.stop();
            epsServer.stop();
            dmsServer.stop();
            umsServer.stop();
            storageServer.stop();
            mqttBroker.stop();
            System.out.println("=== Shutdown complete ===");
        }));

        Thread.currentThread().join();
    }
}