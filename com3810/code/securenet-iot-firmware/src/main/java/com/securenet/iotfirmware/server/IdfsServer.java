package com.securenet.iotfirmware.server;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.paho.client.mqttv3.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP server for the IoT Device Firmware Service (IDFS).
 *
 * <p>IDFS is the device-facing entry point into the SecureNet cloud.
 * Devices call IDFS over HTTP for onboarding and provisioning; the
 * platform calls IDFS to dispatch commands to devices via MQTT.
 *
 * <h3>Device-facing endpoints</h3>
 * <ul>
 *   <li>{@code POST /register}   — bootstrap registration relay to DMS</li>
 *   <li>{@code POST /provision}  — runtime MQTT credential provisioning</li>
 *   <li>{@code POST /heartbeat}  — heartbeat relay to DMS</li>
 * </ul>
 *
 * <h3>Platform-facing endpoints (called by DMS)</h3>
 * <ul>
 *   <li>{@code POST /command}    — publish a command to a device's MQTT
 *       command topic and wait for the device's ack (with timeout)</li>
 * </ul>
 *
 * <p>IDFS maintains a persistent MQTT client connection to the broker.
 * It subscribes to all device ack topics
 * ({@code securenet/devices/+/acks}) and uses a correlation-ID-keyed
 * map of {@link CompletableFuture}s to match acks to waiting command
 * requests.
 */
public class IdfsServer {

    private static final int COMMAND_TIMEOUT_SECONDS = 10;

    private final String host;
    private final int port;
    private final String dmsBaseUrl;
    private final String epsBaseUrl;
    private final String mqttBrokerUrl;
    private final ServiceClient httpClient;
    private HttpServer httpServer;

    /** MQTT client used to publish commands and receive acks. */
    private MqttClient mqttClient;

    /**
     * Pending command futures keyed by correlationId.
     * When a device publishes an ack with a matching correlationId,
     * the future is completed with the ack payload.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Map>> pendingCommands =
            new ConcurrentHashMap<>();

    /**
     * @param host           bind address (e.g. "0.0.0.0")
     * @param port           port to listen on (default 8080)
     * @param dmsBaseUrl     base URL of the Device Management Service
     * @param epsBaseUrl     base URL of the Event Processing Service
     * @param mqttBrokerUrl  MQTT broker URL (e.g. "tcp://localhost:1883")
     */
    public IdfsServer(String host, int port, String dmsBaseUrl,
                      String epsBaseUrl, String mqttBrokerUrl) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.dmsBaseUrl = Objects.requireNonNull(dmsBaseUrl, "dmsBaseUrl");
        this.epsBaseUrl = Objects.requireNonNull(epsBaseUrl, "epsBaseUrl");
        this.mqttBrokerUrl = Objects.requireNonNull(mqttBrokerUrl, "mqttBrokerUrl");
        this.httpClient = new ServiceClient();
    }

    public void start() throws IOException {
        // Connect MQTT client for command dispatch / ack listening
        connectMqtt();

        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(16));

        // Device-facing endpoints
        httpServer.createContext("/register", this::handleBootstrapRegister);
        httpServer.createContext("/provision", this::handleRuntimeProvision);
        httpServer.createContext("/heartbeat", this::handleHeartbeat);

        // Platform-facing endpoint (called by DMS)
        httpServer.createContext("/command", this::handleCommand);

        httpServer.createContext("/health", ex -> writeJson(ex, 200, Map.of("status", "UP")));

        httpServer.start();
        System.out.println("[IDFS] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        disconnectMqtt();
        System.out.println("[IDFS] stopped");
    }

    // =====================================================================
    // MQTT connection for command dispatch
    // =====================================================================

    private void connectMqtt() {
        try {
            String clientId = "idfs-command-dispatcher-" + UUID.randomUUID().toString().substring(0, 8);
            mqttClient = new MqttClient(mqttBrokerUrl, clientId);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            mqttClient.connect(opts);

            // Subscribe to all device ack topics
            String ackTopic = "securenet/devices/+/acks";
            mqttClient.subscribe(ackTopic, 1, this::onAckReceived);

            // Subscribe to all device event topics — forward to EPS
            String eventTopic = "securenet/devices/+/events/#";
            mqttClient.subscribe(eventTopic, 1, this::onDeviceEventReceived);

            System.out.println("[IDFS] MQTT connected to " + mqttBrokerUrl);
            System.out.println("[IDFS] Subscribed to " + ackTopic);
            System.out.println("[IDFS] Subscribed to " + eventTopic);
        } catch (MqttException e) {
            System.err.println("[IDFS] Failed to connect MQTT: " + e.getMessage());
            throw new RuntimeException("IDFS requires MQTT broker", e);
        }
    }

    private void disconnectMqtt() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) mqttClient.disconnect();
                mqttClient.close();
            } catch (MqttException e) {
                System.err.println("[IDFS] Error disconnecting MQTT: " + e.getMessage());
            }
        }
    }

    /**
     * Called when a device publishes an ack on
     * {@code securenet/devices/{deviceId}/acks}.
     *
     * <p>Extracts the correlation_id from the payload and completes the
     * matching {@link CompletableFuture} in {@link #pendingCommands}.
     */
    private void onAckReceived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            Map ack = JsonUtil.fromJson(payload, Map.class);
            String correlationId = (String) ack.get("correlation_id");

            if (correlationId != null) {
                CompletableFuture<Map> future = pendingCommands.remove(correlationId);
                if (future != null) {
                    future.complete(ack);
                    System.out.println("[IDFS] Ack received for correlationId=" + correlationId);
                }
            }
        } catch (Exception e) {
            System.err.println("[IDFS] Error processing ack: " + e.getMessage());
        }
    }

    /**
     * Called when a device publishes an event on
     * {@code securenet/devices/{deviceId}/events/{subtopic}}.
     *
     * <p>Extracts the event payload, transforms it into the format
     * expected by the EPS {@code POST /eps/events/ingest} endpoint,
     * and forwards it over HTTP.
     *
     * <p>This is the bridge between the MQTT event bus and the
     * HTTP-based Event Processing Service.
     */
    private void onDeviceEventReceived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            Map event = JsonUtil.fromJson(payload, Map.class);

            String deviceId = (String) event.get("device_id");
            String eventType = (String) event.get("event_type");

            if (deviceId == null || eventType == null) {
                System.err.println("[IDFS] Ignoring malformed event on " + topic);
                return;
            }

            // Extract nonce and timestamp
            Object nonceObj = event.get("nonce");
            String nonce = (nonceObj != null) ? String.valueOf(nonceObj) : "";

            Object occurredAtObj = event.get("occurred_at_ms");
            String occurredAt;
            if (occurredAtObj instanceof Number) {
                occurredAt = Instant.ofEpochMilli(((Number) occurredAtObj).longValue()).toString();
            } else {
                occurredAt = Instant.now().toString();
            }

            // Build metadata from all fields except the top-level ones
            Map<String, String> metadata = new HashMap<>();
            event.forEach((k, v) -> {
                String key = String.valueOf(k);
                if (!"device_id".equals(key) && !"event_type".equals(key) &&
                        !"occurred_at_ms".equals(key) && !"nonce".equals(key)) {
                    metadata.put(key, String.valueOf(v));
                }
            });

            // Forward to EPS
            Map<String, Object> epsRequest = new HashMap<>();
            epsRequest.put("deviceId", deviceId);
            epsRequest.put("eventType", eventType);
            epsRequest.put("occurredAt", occurredAt);
            epsRequest.put("nonce", nonce);
            epsRequest.put("metadata", metadata);

            ServiceResponse epsResponse = httpClient.post(
                    epsBaseUrl + "/eps/events/ingest", epsRequest);

            if (epsResponse.isSuccess()) {
                System.out.println("[IDFS] Event forwarded to EPS: " + eventType +
                        " from " + deviceId);
            } else {
                System.err.println("[IDFS] EPS rejected event: HTTP " +
                        epsResponse.statusCode() + " " + epsResponse.body());
            }
        } catch (Exception e) {
            System.err.println("[IDFS] Error forwarding event to EPS: " + e.getMessage());
        }
    }

    // =====================================================================
    // POST /command — platform-facing command dispatch
    // =====================================================================

    /**
     * Handles {@code POST /command} from DMS.
     *
     * <p>Expected JSON body:
     * <pre>{@code
     * {
     *   "device_id": "lock-001",
     *   "command_type": "LOCK",
     *   "correlation_id": "uuid-here"   // optional, generated if absent
     * }
     * }</pre>
     *
     * <p>Flow:
     * <ol>
     *   <li>Generate correlationId if not provided</li>
     *   <li>Register a CompletableFuture keyed by correlationId</li>
     *   <li>Publish command to {@code securenet/devices/{deviceId}/commands/{type}}</li>
     *   <li>Wait for device ack (up to COMMAND_TIMEOUT_SECONDS)</li>
     *   <li>Return ack result or timeout error</li>
     * </ol>
     */
    private void handleCommand(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            String requestBody = readBodyString(ex);
            Map body = JsonUtil.fromJson(requestBody, Map.class);

            String deviceId = (String) body.get("device_id");
            String commandType = (String) body.get("command_type");
            String correlationId = (String) body.get("correlation_id");

            if (isBlank(deviceId) || isBlank(commandType)) {
                writeJson(ex, 400, Map.of("error", "device_id and command_type are required"));
                return;
            }

            if (isBlank(correlationId)) {
                correlationId = UUID.randomUUID().toString();
            }

            if (mqttClient == null || !mqttClient.isConnected()) {
                writeJson(ex, 503, Map.of("error", "MQTT broker not connected"));
                return;
            }

            System.out.println("[IDFS] Command dispatch: deviceId=" + deviceId +
                    " command=" + commandType + " correlationId=" + correlationId);

            // Register future before publishing (avoid race with fast ack)
            CompletableFuture<Map> ackFuture = new CompletableFuture<>();
            pendingCommands.put(correlationId, ackFuture);

            // Publish command to device's MQTT topic
            String topic = "securenet/devices/" + deviceId + "/commands/" + commandType.toLowerCase();
            String payload = JsonUtil.toJson(Map.of(
                    "device_id", deviceId,
                    "command_type", commandType,
                    "correlation_id", correlationId
            ));

            mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
            System.out.println("[IDFS] Published command to " + topic);

            // Wait for ack with timeout
            try {
                Map ack = ackFuture.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                Boolean success = (Boolean) ack.get("success");
                if (Boolean.TRUE.equals(success)) {
                    writeJson(ex, 200, Map.of(
                            "acknowledged", true,
                            "correlation_id", correlationId,
                            "device_id", deviceId,
                            "command_type", commandType
                    ));
                } else {
                    writeJson(ex, 200, Map.of(
                            "acknowledged", false,
                            "correlation_id", correlationId,
                            "device_id", deviceId,
                            "command_type", commandType,
                            "error", "Device reported failure"
                    ));
                }
            } catch (TimeoutException e) {
                pendingCommands.remove(correlationId);
                System.out.println("[IDFS] Command timed out: correlationId=" + correlationId);
                writeJson(ex, 504, Map.of(
                        "acknowledged", false,
                        "correlation_id", correlationId,
                        "error", "Device did not acknowledge within " + COMMAND_TIMEOUT_SECONDS + "s"
                ));
            }

        } catch (MqttException e) {
            System.err.println("[IDFS] MQTT publish error: " + e.getMessage());
            writeJson(ex, 503, Map.of("error", "Failed to publish command to MQTT"));
        } catch (Exception e) {
            System.err.println("[IDFS] Command dispatch error: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", "Internal Server Error"));
        }
    }

    // =====================================================================
    // POST /register — bootstrap registration relay
    // =====================================================================

    private void handleBootstrapRegister(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
                writeJson(ex, 400, Map.of("error", "Content-Type must be application/json"));
                return;
            }

            String requestBody = readBodyString(ex);
            Map body = JsonUtil.fromJson(requestBody, Map.class);

            String deviceId = (String) body.get("device_id");
            String registrationToken = (String) body.get("registration_token");

            if (isBlank(deviceId) || isBlank(registrationToken)) {
                writeJson(ex, 400, Map.of("error", "device_id and registration_token are required"));
                return;
            }

            System.out.println("[IDFS] Bootstrap registration request: deviceId=" + deviceId);

            Map<String, String> dmsRequest = Map.of(
                    "deviceId", deviceId,
                    "registrationToken", registrationToken
            );

            ServiceResponse dmsResponse = httpClient.post(
                    dmsBaseUrl + "/dms/devices/accept-registration",
                    dmsRequest
            );

            if (dmsResponse.isSuccess()) {
                Map dmsResult = JsonUtil.fromJson(dmsResponse.body(), Map.class);
                Map regInfo = (Map) dmsResult.get("registrationInfo");
                Map fwAssignment = (Map) dmsResult.get("firmwareAssignment");

                Map<String, String> deviceResponse = Map.of(
                        "device_id", (String) regInfo.get("deviceId"),
                        "device_type", (String) regInfo.get("deviceType"),
                        "registered_at", (String) regInfo.get("registeredAt"),
                        "firmware_version", (String) fwAssignment.get("firmwareVersion"),
                        "firmware_url", (String) fwAssignment.get("firmwareUrl"),
                        "firmware_issued_at", (String) fwAssignment.get("issuedAt")
                );

                System.out.println("[IDFS] Bootstrap registration succeeded for deviceId=" + deviceId);
                writeJson(ex, 200, deviceResponse);
            } else {
                System.out.println("[IDFS] DMS returned " + dmsResponse.statusCode() +
                        " for deviceId=" + deviceId + ": " + dmsResponse.body());
                writeRaw(ex, dmsResponse.statusCode(), dmsResponse.body());
            }
        } catch (Exception e) {
            System.out.println("[IDFS] Bootstrap registration error: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", "Internal Server Error"));
        }
    }

    // =====================================================================
    // POST /provision — runtime MQTT credential provisioning
    // =====================================================================

    private void handleRuntimeProvision(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            String requestBody = readBodyString(ex);
            Map body = JsonUtil.fromJson(requestBody, Map.class);
            String deviceId = (String) body.get("device_id");

            if (isBlank(deviceId)) {
                writeJson(ex, 400, Map.of("error", "device_id is required"));
                return;
            }

            System.out.println("[IDFS] Runtime provisioning request: deviceId=" + deviceId);

            ServiceResponse dmsResponse = httpClient.get(
                    dmsBaseUrl + "/dms/devices/get?deviceId=" + deviceId
            );

            if (!dmsResponse.isSuccess()) {
                writeJson(ex, dmsResponse.statusCode(),
                        Map.of("error", "Device not found or not registered"));
                return;
            }

            String mqttClientId = "securenet-device-" + deviceId;
            String mqttUsername = "device-" + deviceId;
            String mqttPassword = "mqtt-pass-" + deviceId + "-" + System.currentTimeMillis();

            Map<String, String> credentials = Map.of(
                    "device_id", deviceId,
                    "mqtt_broker_url", mqttBrokerUrl,
                    "mqtt_client_id", mqttClientId,
                    "mqtt_username", mqttUsername,
                    "mqtt_password", mqttPassword
            );

            System.out.println("[IDFS] Runtime provisioning succeeded for deviceId=" + deviceId);
            writeJson(ex, 200, credentials);
        } catch (Exception e) {
            System.out.println("[IDFS] Runtime provisioning error: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", "Internal Server Error"));
        }
    }

    // =====================================================================
    // POST /heartbeat — relay to DMS
    // =====================================================================

    private void handleHeartbeat(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            String requestBody = readBodyString(ex);
            Map body = JsonUtil.fromJson(requestBody, Map.class);
            String deviceId = (String) body.get("device_id");

            if (isBlank(deviceId)) {
                writeJson(ex, 400, Map.of("error", "device_id is required"));
                return;
            }

            ServiceResponse dmsResponse = httpClient.post(
                    dmsBaseUrl + "/dms/devices/heartbeat",
                    Map.of("deviceId", deviceId)
            );

            writeRaw(ex, dmsResponse.statusCode(), dmsResponse.body());
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", "Internal Server Error"));
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String readBodyString(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeJson(HttpExchange ex, int statusCode, Object body) throws IOException {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void writeRaw(HttpExchange ex, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}