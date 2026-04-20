package com.securenet.demo;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.model.DeviceType;
import org.eclipse.paho.client.mqttv3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Java mock of a SecureNet IoT device, replicating the behavior of the
 * ESP32 C firmware for end-to-end testing within a pure-Java environment.
 *
 * <p>The mock device executes the same two-phase onboarding flow as the
 * real firmware:
 *
 * <h3>Phase 1 — Bootstrap (bootloader)</h3>
 * <ol>
 *   <li>"Power on" — load device config (identity, registration token)</li>
 *   <li>"Connect WiFi" — simulated (always succeeds in mock)</li>
 *   <li>POST {@code /register} to IDFS with device_id + registration_token</li>
 *   <li>Receive BootstrapRegistrationResult (firmware assignment)</li>
 *   <li>"Install firmware" — simulated</li>
 * </ol>
 *
 * <h3>Phase 2 — Runtime (installed firmware)</h3>
 * <ol>
 *   <li>POST {@code /provision} to IDFS to get MQTT credentials</li>
 *   <li>Connect to MQTT broker</li>
 *   <li>Begin sending heartbeats every 30 seconds</li>
 *   <li>Publish simulated events (motion detected, etc.)</li>
 * </ol>
 *
 * <p>This mirrors the C code in {@code bootloader_runtime.c} and
 * {@code device_runtime.c} with exponential backoff on failures.
 */
public class MockDevice implements Runnable {

    private static final int INITIAL_BACKOFF_MS = 1000;
    private static final int MAX_BACKOFF_MS = 15000;
    private static final int HEARTBEAT_INTERVAL_MS = 30_000;

    private final String deviceId;
    private final String registrationToken;
    private final DeviceType deviceType;
    private final String idfsBaseUrl;
    private final ServiceClient httpClient;

    // ----- State set during onboarding -----
    private volatile String firmwareVersion;
    private volatile String firmwareUrl;
    private volatile String mqttBrokerUrl;
    private volatile String mqttClientId;
    private volatile String mqttUsername;
    private volatile String mqttPassword;

    // ----- Runtime state -----
    private MqttClient mqttClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong eventNonce = new AtomicLong(0);

    /**
     * @param deviceId          unique device identifier (matches QR code)
     * @param registrationToken one-time bootstrap token
     * @param deviceType        hardware category
     * @param idfsBaseUrl       URL of the IDFS server, e.g. "http://localhost:8080"
     */
    public MockDevice(String deviceId, String registrationToken,
                      DeviceType deviceType, String idfsBaseUrl) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.registrationToken = Objects.requireNonNull(registrationToken, "registrationToken");
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType");
        this.idfsBaseUrl = Objects.requireNonNull(idfsBaseUrl, "idfsBaseUrl");
        this.httpClient = new ServiceClient();
    }

    /**
     * Executes the full device lifecycle: bootstrap → provision → runtime.
     * Intended to be run on its own thread.
     */
    @Override
    public void run() {
        running.set(true);
        String tag = "[MockDevice:" + deviceId + "]";

        try {
            System.out.println(tag + " powered on");

            // ============================================================
            // Phase 1: Bootstrap — register with cloud via IDFS
            // ============================================================
            System.out.println(tag + " Phase 1: Bootstrap registration");
            Map bootstrapResult = bootstrapRegisterWithRetry(tag);

            firmwareVersion = (String) bootstrapResult.get("firmware_version");
            firmwareUrl = (String) bootstrapResult.get("firmware_url");
            System.out.println(tag + " Bootstrap succeeded. firmware=" + firmwareVersion +
                    " url=" + firmwareUrl);

            // Simulate firmware installation
            System.out.println(tag + " Installing firmware " + firmwareVersion + "...");
            Thread.sleep(500); // simulate install time
            System.out.println(tag + " Firmware installed. Rebooting into installed firmware...");
            Thread.sleep(200); // simulate reboot

            // ============================================================
            // Phase 2: Runtime provisioning — get MQTT credentials
            // ============================================================
            System.out.println(tag + " Phase 2: Runtime provisioning");
            Map provisionResult = runtimeProvisionWithRetry(tag);

            mqttBrokerUrl = (String) provisionResult.get("mqtt_broker_url");
            mqttClientId = (String) provisionResult.get("mqtt_client_id");
            mqttUsername = (String) provisionResult.get("mqtt_username");
            mqttPassword = (String) provisionResult.get("mqtt_password");
            System.out.println(tag + " Provisioned. broker=" + mqttBrokerUrl +
                    " clientId=" + mqttClientId);

            // ============================================================
            // Phase 3: Connect MQTT and enter steady state
            // ============================================================
            System.out.println(tag + " Phase 3: Connecting to MQTT broker");
            connectMqtt(tag);

            System.out.println(tag + " Entering steady-state runtime loop");
            steadyStateLoop(tag);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(tag + " interrupted, shutting down");
        } catch (Exception e) {
            System.out.println(tag + " fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running.set(false);
            disconnectMqtt(tag);
            System.out.println(tag + " shut down");
        }
    }

    /** Signals the device to stop its runtime loop. */
    public void shutdown() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getDeviceId() {
        return deviceId;
    }

    // =====================================================================
    // Phase 1: Bootstrap registration with exponential backoff
    // =====================================================================

    private Map bootstrapRegisterWithRetry(String tag) throws InterruptedException {
        int backoffMs = INITIAL_BACKOFF_MS;

        while (running.get()) {
            try {
                ServiceResponse resp = httpClient.post(
                        idfsBaseUrl + "/register",
                        Map.of(
                                "device_id", deviceId,
                                "registration_token", registrationToken
                        )
                );

                if (resp.statusCode() == 200) {
                    return JsonUtil.fromJson(resp.body(), Map.class);
                }

                System.out.println(tag + " Bootstrap registration failed: HTTP " +
                        resp.statusCode() + " " + resp.body());
            } catch (IOException e) {
                System.out.println(tag + " Bootstrap registration error: " + e.getMessage());
            }

            System.out.println(tag + " Retrying in " + backoffMs + "ms");
            Thread.sleep(backoffMs);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }

        throw new InterruptedException("Device shutdown during bootstrap");
    }

    // =====================================================================
    // Phase 2: Runtime provisioning with exponential backoff
    // =====================================================================

    private Map runtimeProvisionWithRetry(String tag) throws InterruptedException {
        int backoffMs = INITIAL_BACKOFF_MS;

        while (running.get()) {
            try {
                ServiceResponse resp = httpClient.post(
                        idfsBaseUrl + "/provision",
                        Map.of("device_id", deviceId)
                );

                if (resp.statusCode() == 200) {
                    return JsonUtil.fromJson(resp.body(), Map.class);
                }

                System.out.println(tag + " Runtime provisioning failed: HTTP " +
                        resp.statusCode() + " " + resp.body());
            } catch (IOException e) {
                System.out.println(tag + " Runtime provisioning error: " + e.getMessage());
            }

            System.out.println(tag + " Retrying in " + backoffMs + "ms");
            Thread.sleep(backoffMs);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }

        throw new InterruptedException("Device shutdown during provisioning");
    }

    // =====================================================================
    // Phase 3: MQTT connection and steady-state loop
    // =====================================================================

    private void connectMqtt(String tag) throws MqttException, InterruptedException {
        int backoffMs = INITIAL_BACKOFF_MS;

        while (running.get()) {
            try {
                mqttClient = new MqttClient(mqttBrokerUrl, mqttClientId);

                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setUserName(mqttUsername);
                opts.setPassword(mqttPassword.toCharArray());
                opts.setAutomaticReconnect(true);
                opts.setCleanSession(true);

                mqttClient.connect(opts);
                System.out.println(tag + " MQTT connected to " + mqttBrokerUrl);

                // Subscribe to command topic
                String commandTopic = "securenet/devices/" + deviceId + "/commands/#";
                mqttClient.subscribe(commandTopic, 1, (topic, message) -> {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    System.out.println(tag + " Received command: topic=" + topic +
                            " payload=" + payload);
                    handleCommand(tag, topic, payload);
                });
                System.out.println(tag + " Subscribed to " + commandTopic);
                return;

            } catch (MqttException e) {
                System.out.println(tag + " MQTT connect failed: " + e.getMessage());
                Thread.sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
    }

    /**
     * Steady-state loop: sends heartbeats and periodic simulated events.
     * Mirrors the device_runtime behavior from the C firmware.
     */
    private void steadyStateLoop(String tag) throws InterruptedException {
        int heartbeatCount = 0;

        while (running.get()) {
            // Send heartbeat to IDFS (which relays to DMS)
            try {
                ServiceResponse resp = httpClient.post(
                        idfsBaseUrl + "/heartbeat",
                        Map.of("device_id", deviceId)
                );
                if (resp.isSuccess()) {
                    heartbeatCount++;
                    if (heartbeatCount % 5 == 0) {
                        System.out.println(tag + " heartbeat #" + heartbeatCount + " sent");
                    }
                }
            } catch (IOException e) {
                System.out.println(tag + " heartbeat failed: " + e.getMessage());
            }

            // Every 3rd heartbeat (90s), simulate a motion event for sensors/cameras
            if (heartbeatCount > 0 && heartbeatCount % 3 == 0 &&
                    (deviceType == DeviceType.MOTION_SENSOR || deviceType == DeviceType.CAMERA)) {
                publishMotionEvent(tag);
            }

            Thread.sleep(HEARTBEAT_INTERVAL_MS);
        }
    }

    // =====================================================================
    // Event publishing (mirrors mqtt_manager_publish_motion_detected in C)
    // =====================================================================

    private void publishMotionEvent(String tag) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            System.out.println(tag + " Cannot publish event — MQTT not connected");
            return;
        }

        try {
            String topic = "securenet/devices/" + deviceId + "/events/motion";
            long nonce = eventNonce.incrementAndGet();
            long occurredAtMs = System.currentTimeMillis();

            String payload = JsonUtil.toJson(Map.of(
                    "device_id", deviceId,
                    "event_type", "MOTION_DETECTED",
                    "motion_detected", true,
                    "occurred_at_ms", occurredAtMs,
                    "nonce", nonce
            ));

            mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
            System.out.println(tag + " Published MOTION_DETECTED event nonce=" + nonce);
        } catch (MqttException e) {
            System.out.println(tag + " Failed to publish motion event: " + e.getMessage());
        }
    }

    // =====================================================================
    // Command handling
    // =====================================================================

    private void handleCommand(String tag, String topic, String payload) {
        try {
            Map cmd = JsonUtil.fromJson(payload, Map.class);
            String commandType = (String) cmd.get("command_type");
            String correlationId = (String) cmd.get("correlation_id");

            System.out.println(tag + " Executing command: " + commandType +
                    " correlationId=" + correlationId);

            // Simulate command execution
            boolean success = true;
            String ackTopic = "securenet/devices/" + deviceId + "/acks";
            String ackPayload = JsonUtil.toJson(Map.of(
                    "device_id", deviceId,
                    "correlation_id", correlationId != null ? correlationId : "",
                    "command_type", commandType != null ? commandType : "",
                    "success", success
            ));

            mqttClient.publish(ackTopic, ackPayload.getBytes(StandardCharsets.UTF_8), 1, false);
            System.out.println(tag + " Published ack for " + commandType);
        } catch (Exception e) {
            System.out.println(tag + " Error handling command: " + e.getMessage());
        }
    }

    // =====================================================================
    // Cleanup
    // =====================================================================

    private void disconnectMqtt(String tag) {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
            } catch (MqttException e) {
                System.out.println(tag + " Error disconnecting MQTT: " + e.getMessage());
            }
        }
    }
}
