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
 * Base class for all mock SecureNet IoT devices.
 *
 * <p>Implements the full two-phase onboarding lifecycle that mirrors the
 * ESP32 C firmware (bootloader_runtime.c → device_runtime.c):
 *
 * <ol>
 *   <li>Phase 1 — Bootstrap: POST /register to IDFS, receive firmware assignment</li>
 *   <li>Phase 2 — Runtime provisioning: POST /provision to IDFS, receive MQTT credentials</li>
 *   <li>Phase 3 — Steady state: connect MQTT, send heartbeats, handle commands</li>
 * </ol>
 *
 * <p>Subclasses implement {@link #onSteadyStateTick(String, int)} to define
 * device-specific behavior and {@link #onCommandReceived(String, String, String)}
 * to handle incoming MQTT commands. Subclasses that need the full command payload
 * (e.g. to extract {@code session_id} for streaming) can additionally override
 * {@link #onFullCommandReceived(String, Map)}.
 */
public abstract class AbstractMockDevice implements Runnable {

    private static final int INITIAL_BACKOFF_MS  = 1000;
    private static final int MAX_BACKOFF_MS      = 15000;
    private static final int HEARTBEAT_INTERVAL_MS = 30_000;

    private final String deviceId;
    private final String registrationToken;
    private final DeviceType deviceType;
    private final String idfsBaseUrl;
    protected final ServiceClient httpClient;

    protected volatile String firmwareVersion;
    protected volatile String firmwareUrl;
    protected volatile String mqttBrokerUrl;
    protected volatile String mqttClientId;
    protected volatile String mqttUsername;
    protected volatile String mqttPassword;

    protected MqttClient mqttClient;
    protected final AtomicBoolean running    = new AtomicBoolean(false);
    protected final AtomicLong eventNonce    = new AtomicLong(0);

    protected AbstractMockDevice(String deviceId, String registrationToken,
                                 DeviceType deviceType, String idfsBaseUrl) {
        this.deviceId          = Objects.requireNonNull(deviceId, "deviceId");
        this.registrationToken = Objects.requireNonNull(registrationToken, "registrationToken");
        this.deviceType        = Objects.requireNonNull(deviceType, "deviceType");
        this.idfsBaseUrl       = Objects.requireNonNull(idfsBaseUrl, "idfsBaseUrl");
        this.httpClient        = new ServiceClient();
    }

    // =====================================================================
    // Accessors
    // =====================================================================

    public String getDeviceId()    { return deviceId; }
    public DeviceType getDeviceType() { return deviceType; }
    public boolean isRunning()     { return running.get(); }
    public void shutdown()         { running.set(false); }

    protected String tag() {
        return "[" + getClass().getSimpleName() + ":" + deviceId + "]";
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    public final void run() {
        running.set(true);
        String t = tag();

        try {
            System.out.println(t + " powered on");

            System.out.println(t + " Phase 1: Bootstrap registration");
            Map bootstrapResult = bootstrapRegisterWithRetry(t);
            firmwareVersion = (String) bootstrapResult.get("firmware_version");
            firmwareUrl     = (String) bootstrapResult.get("firmware_url");
            System.out.println(t + " Bootstrap succeeded. firmware=" + firmwareVersion
                    + " url=" + firmwareUrl);

            System.out.println(t + " Installing firmware " + firmwareVersion + "...");
            Thread.sleep(500);
            System.out.println(t + " Firmware installed. Rebooting into installed firmware...");
            Thread.sleep(200);

            System.out.println(t + " Phase 2: Runtime provisioning");
            Map provisionResult = runtimeProvisionWithRetry(t);
            mqttBrokerUrl = (String) provisionResult.get("mqtt_broker_url");
            mqttClientId  = (String) provisionResult.get("mqtt_client_id");
            mqttUsername  = (String) provisionResult.get("mqtt_username");
            mqttPassword  = (String) provisionResult.get("mqtt_password");
            System.out.println(t + " Provisioned. broker=" + mqttBrokerUrl
                    + " clientId=" + mqttClientId);

            System.out.println(t + " Phase 3: Connecting to MQTT broker");
            connectMqtt(t);
            System.out.println(t + " Entering steady-state runtime loop");
            steadyStateLoop(t);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(t + " interrupted, shutting down");
        } catch (Exception e) {
            System.out.println(t + " fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running.set(false);
            disconnectMqtt(t);
            System.out.println(t + " shut down");
        }
    }

    // =====================================================================
    // Subclass hooks
    // =====================================================================

    /**
     * Called once per heartbeat interval during steady state.
     */
    protected abstract void onSteadyStateTick(String tag, int heartbeatCount);

    /**
     * Called when an MQTT command is received. Only {@code commandType}
     * and {@code correlationId} are provided. Subclasses that need the
     * full payload (e.g. {@code session_id}) should override
     * {@link #onFullCommandReceived(String, Map)} instead.
     */
    protected abstract void onCommandReceived(String tag, String commandType,
                                              String correlationId);

    /**
     * Called with the full command payload map when an MQTT command arrives.
     * Default implementation delegates to
     * {@link #onCommandReceived(String, String, String)}, extracting only
     * {@code command_type} and {@code correlation_id}.
     *
     * <p>Subclasses such as {@link MockCamera} override this to also extract
     * {@code session_id} and {@code vss_url} from the STREAM_START payload.
     *
     * @param tag the log prefix
     * @param cmd the full parsed command map from MQTT
     */
    protected void onFullCommandReceived(String tag, Map cmd) {
        onCommandReceived(tag,
                (String) cmd.get("command_type"),
                (String) cmd.get("correlation_id"));
    }

    // =====================================================================
    // Protected helpers for subclasses
    // =====================================================================

    protected void publishEvent(String subtopic, Map<String, Object> payload) {
        if (mqttClient == null || !mqttClient.isConnected()) return;
        try {
            String topic = "securenet/devices/" + deviceId + "/events/" + subtopic;
            String json  = JsonUtil.toJson(payload);
            mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, false);
        } catch (MqttException e) {
            System.out.println(tag() + " Failed to publish event: " + e.getMessage());
        }
    }

    protected void publishAck(String correlationId, String commandType, boolean success) {
        if (mqttClient == null || !mqttClient.isConnected()) return;
        try {
            String topic = "securenet/devices/" + deviceId + "/acks";
            String json  = JsonUtil.toJson(Map.of(
                    "device_id",      deviceId,
                    "correlation_id", correlationId != null ? correlationId : "",
                    "command_type",   commandType   != null ? commandType   : "",
                    "success",        success
            ));
            mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, false);
        } catch (MqttException e) {
            System.out.println(tag() + " Failed to publish ack: " + e.getMessage());
        }
    }

    protected long nextNonce() { return eventNonce.incrementAndGet(); }

    // =====================================================================
    // Bootstrap registration (Phase 1)
    // =====================================================================

    private Map bootstrapRegisterWithRetry(String t) throws InterruptedException {
        int backoffMs = INITIAL_BACKOFF_MS;
        while (running.get()) {
            try {
                ServiceResponse resp = httpClient.post(
                        idfsBaseUrl + "/register",
                        Map.of("device_id", deviceId,
                                "registration_token", registrationToken));
                if (resp.statusCode() == 200)
                    return JsonUtil.fromJson(resp.body(), Map.class);
                System.out.println(t + " Bootstrap failed: HTTP " + resp.statusCode());
            } catch (IOException e) {
                System.out.println(t + " Bootstrap error: " + e.getMessage());
            }
            System.out.println(t + " Retrying in " + backoffMs + "ms");
            Thread.sleep(backoffMs);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
        throw new InterruptedException("Device shutdown during bootstrap");
    }

    // =====================================================================
    // Runtime provisioning (Phase 2)
    // =====================================================================

    private Map runtimeProvisionWithRetry(String t) throws InterruptedException {
        int backoffMs = INITIAL_BACKOFF_MS;
        while (running.get()) {
            try {
                ServiceResponse resp = httpClient.post(
                        idfsBaseUrl + "/provision",
                        Map.of("device_id", deviceId));
                if (resp.statusCode() == 200)
                    return JsonUtil.fromJson(resp.body(), Map.class);
                System.out.println(t + " Provisioning failed: HTTP " + resp.statusCode());
            } catch (IOException e) {
                System.out.println(t + " Provisioning error: " + e.getMessage());
            }
            System.out.println(t + " Retrying in " + backoffMs + "ms");
            Thread.sleep(backoffMs);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
        throw new InterruptedException("Device shutdown during provisioning");
    }

    // =====================================================================
    // MQTT connection (Phase 3)
    // =====================================================================

    private void connectMqtt(String t) throws MqttException, InterruptedException {
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
                System.out.println(t + " MQTT connected to " + mqttBrokerUrl);

                String commandTopic = "securenet/devices/" + deviceId + "/commands/#";
                mqttClient.subscribe(commandTopic, 1, (topic, message) -> {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    System.out.println(t + " Received command: " + payload);
                    try {
                        Map cmd = JsonUtil.fromJson(payload, Map.class);
                        // Call onFullCommandReceived so subclasses get the
                        // entire payload (including session_id, vss_url, etc.)
                        onFullCommandReceived(t, cmd);
                    } catch (Exception e) {
                        System.out.println(t + " Error handling command: " + e.getMessage());
                    }
                });
                System.out.println(t + " Subscribed to " + commandTopic);
                return;
            } catch (MqttException e) {
                System.out.println(t + " MQTT connect failed: " + e.getMessage());
                Thread.sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
    }

    // =====================================================================
    // Steady-state loop
    // =====================================================================

    private void steadyStateLoop(String t) throws InterruptedException {
        int heartbeatCount = 0;
        while (running.get()) {
            try {
                httpClient.post(idfsBaseUrl + "/heartbeat",
                        Map.of("device_id", deviceId));
                heartbeatCount++;
                if (heartbeatCount % 5 == 0) {
                    System.out.println(t + " heartbeat #" + heartbeatCount);
                }
            } catch (IOException e) {
                System.out.println(t + " heartbeat failed: " + e.getMessage());
            }

            onSteadyStateTick(t, heartbeatCount);
            Thread.sleep(HEARTBEAT_INTERVAL_MS);
        }
    }

    private void disconnectMqtt(String t) {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) mqttClient.disconnect();
                mqttClient.close();
            } catch (MqttException e) {
                System.out.println(t + " Error disconnecting MQTT: " + e.getMessage());
            }
        }
    }
}