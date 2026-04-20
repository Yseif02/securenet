package com.securenet.iotfirmware.server;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.storage.StorageGateway;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.paho.client.mqttv3.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * HTTP server for the IoT Device Firmware Service (IDFS).
 *
 * <p>IDFS is the device-facing entry point into the SecureNet cloud.
 * Devices call IDFS over HTTP for onboarding and provisioning; the
 * platform calls IDFS to dispatch commands to devices via MQTT.
 *
 * <h3>Device-facing endpoints</h3>
 * <ul>
 *   <li>{@code POST /register}  — bootstrap registration relay to DMS</li>
 *   <li>{@code POST /provision} — runtime MQTT credential provisioning</li>
 *   <li>{@code POST /heartbeat} — heartbeat relay to DMS</li>
 * </ul>
 *
 * <h3>Platform-facing endpoints (called by DMS)</h3>
 * <ul>
 *   <li>{@code POST /command} — publish command to device MQTT topic,
 *       wait for ack</li>
 * </ul>
 */
public class IdfsServer {

    private static final Logger log = Logger.getLogger(IdfsServer.class.getName());

    private static final int COMMAND_TIMEOUT_SECONDS = 10;

    private final String host;
    private final int port;
    private final String dmsBaseUrl;
    private final String epsBaseUrl;
    private final String mqttBrokerUrl;
    private final ServiceClient httpClient;
    private HttpServer httpServer;
    private final StorageGateway storageGateway;

    private MqttClient mqttClient;

    /**
     * Pending command futures keyed by correlationId.
     * Completed when the device publishes an ack with a matching correlationId.
     */
    //private final ConcurrentHashMap<String, CompletableFuture<Map>> pendingCommands =
           // new ConcurrentHashMap<>();

    public IdfsServer(String host, int port, String dmsBaseUrl,
                      String epsBaseUrl, String mqttBrokerUrl,
                      StorageGateway storageGateway) {
        this.host          = Objects.requireNonNull(host, "host");
        this.port          = port;
        this.dmsBaseUrl    = Objects.requireNonNull(dmsBaseUrl, "dmsBaseUrl");
        this.epsBaseUrl    = Objects.requireNonNull(epsBaseUrl, "epsBaseUrl");
        this.mqttBrokerUrl = Objects.requireNonNull(mqttBrokerUrl, "mqttBrokerUrl");
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");

        this.httpClient    = new ServiceClient();
    }

    public void start() throws IOException {
        connectMqtt();

        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(16));

        httpServer.createContext("/register",  this::handleBootstrapRegister);
        httpServer.createContext("/provision", this::handleRuntimeProvision);
        httpServer.createContext("/heartbeat", this::handleHeartbeat);
        httpServer.createContext("/command",   this::handleCommand);
        httpServer.createContext("/health", ex -> {
            log.fine("[IDFS] Health check from " + ex.getRemoteAddress());
            writeJson(ex, 200, Map.of("status", "UP"));
        });

        httpServer.start();
        log.info("[IDFS] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        disconnectMqtt();
        System.out.println("[IDFS] stopped");
    }

    // =====================================================================
    // MQTT connection
    // =====================================================================

    private void connectMqtt() {
        try {
            String clientId = "idfs-command-dispatcher-"
                    + UUID.randomUUID().toString().substring(0, 8);
            mqttClient = new MqttClient(mqttBrokerUrl, clientId);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            mqttClient.connect(opts);

            String ackTopic   = "securenet/devices/+/acks";
            String eventTopic = "securenet/devices/+/events/#";
            mqttClient.subscribe(ackTopic,   1, this::onAckReceived);
            mqttClient.subscribe(eventTopic, 1, this::onDeviceEventReceived);

            log.info("[IDFS] MQTT connected to " + mqttBrokerUrl);
            log.info("[IDFS] Subscribed to " + ackTopic);
            log.info("[IDFS] Subscribed to " + eventTopic);
        } catch (MqttException e) {
            log.severe("[IDFS] Failed to connect MQTT: " + e.getMessage());
            throw new RuntimeException("IDFS requires MQTT broker", e);
        }
    }

    private void disconnectMqtt() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) mqttClient.disconnect();
                mqttClient.close();
                log.info("[IDFS] MQTT disconnected");
            } catch (MqttException e) {
                log.warning("[IDFS] Error disconnecting MQTT: " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // MQTT ack handler
    // =====================================================================

    private void onAckReceived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            Map ack        = JsonUtil.fromJson(payload, Map.class);
            String corrId  = (String) ack.get("correlation_id");
            if (corrId == null) {
                log.warning("[IDFS] Ack missing correlation_id on topic=" + topic);
                return;
            }
            Boolean success = (Boolean) ack.get("success");
            String result   = Boolean.TRUE.equals(success) ? "SUCCESS" : "FAILURE";
            storageGateway.updatePendingCommandResult(corrId, result);
            log.info("[IDFS] Ack received and persisted: correlationId=" + corrId
                    + " result=" + result);
        } catch (Exception e) {
            log.severe("[IDFS] Error processing ack: " + e.getMessage());
        }
    }

    // =====================================================================
    // MQTT event handler — forward to EPS
    // =====================================================================

    private void onDeviceEventReceived(String topic, MqttMessage message) {
        try {
            String payload  = new String(message.getPayload(), StandardCharsets.UTF_8);
            Map event       = JsonUtil.fromJson(payload, Map.class);

            String deviceId  = (String) event.get("device_id");
            String eventType = (String) event.get("event_type");

            if (deviceId == null || eventType == null) {
                log.warning("[IDFS] Ignoring malformed event on topic=" + topic);
                return;
            }

            log.info("[IDFS] Device event received: topic=" + topic
                    + " deviceId=" + deviceId + " type=" + eventType);

            Object nonceObj    = event.get("nonce");
            String nonce       = (nonceObj != null) ? String.valueOf(nonceObj) : "";
            Object tsObj       = event.get("occurred_at_ms");
            String occurredAt  = (tsObj instanceof Number)
                    ? Instant.ofEpochMilli(((Number) tsObj).longValue()).toString()
                    : Instant.now().toString();

            Map<String, String> metadata = new HashMap<>();
            event.forEach((k, v) -> {
                String key = String.valueOf(k);
                if (!"device_id".equals(key) && !"event_type".equals(key) &&
                        !"occurred_at_ms".equals(key) && !"nonce".equals(key)) {
                    metadata.put(key, String.valueOf(v));
                }
            });

            Map<String, Object> epsRequest = new HashMap<>();
            epsRequest.put("deviceId",   deviceId);
            epsRequest.put("eventType",  eventType);
            epsRequest.put("occurredAt", occurredAt);
            epsRequest.put("nonce",      nonce);
            epsRequest.put("metadata",   metadata);

            ServiceResponse epsResponse = httpClient.post(
                    epsBaseUrl + "/eps/events/ingest", epsRequest);

            if (epsResponse.isSuccess()) {
                log.info("[IDFS] Event forwarded to EPS: " + eventType
                        + " from " + deviceId);
            } else {
                log.warning("[IDFS] EPS rejected event: HTTP "
                        + epsResponse.statusCode() + " deviceId=" + deviceId
                        + " type=" + eventType + " body=" + epsResponse.body());
            }
        } catch (Exception e) {
            log.severe("[IDFS] Error forwarding event to EPS: " + e.getMessage());
        }
    }

    // =====================================================================
    // POST /command
    // =====================================================================

    private void handleCommand(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            String requestBody = readBodyString(ex);
            Map body = JsonUtil.fromJson(requestBody, Map.class);

            String deviceId    = (String) body.get("device_id");
            String commandType = (String) body.get("command_type");
            String corrId      = (String) body.get("correlation_id");

            if (isBlank(deviceId) || isBlank(commandType)) {
                writeJson(ex, 400, Map.of("error", "device_id and command_type are required"));
                return;
            }
            if (isBlank(corrId)) corrId = UUID.randomUUID().toString();

            if (mqttClient == null || !mqttClient.isConnected()) {
                log.severe("[IDFS] Command dispatch failed: MQTT broker not connected"
                        + " deviceId=" + deviceId);
                writeJson(ex, 503, Map.of("error", "MQTT broker not connected"));
                return;
            }

            log.info("[IDFS] Command dispatch: deviceId=" + deviceId
                    + " command=" + commandType + " correlationId=" + corrId);

            // Save to DB before publishing (so any instance can receive the ack)
            Instant now       = Instant.now();
            Instant expiresAt = now.plusSeconds(COMMAND_TIMEOUT_SECONDS);
            storageGateway.savePendingCommand(corrId, deviceId, commandType, now, expiresAt);

            String topic   = "securenet/devices/" + deviceId + "/commands/" + commandType.toLowerCase();
            Map<String, Object> mqttPayload = new HashMap<>(body); // body is already parsed from the request
            mqttPayload.put("device_id",      deviceId);
            mqttPayload.put("command_type",   commandType);
            mqttPayload.put("correlation_id", corrId);
            String payload = JsonUtil.toJson(mqttPayload);

            mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
            log.info("[IDFS] Published command to " + topic);

            // Poll DB for ack result (any IDFS instance may have written it)
            long deadline = System.currentTimeMillis() + (COMMAND_TIMEOUT_SECONDS * 1000L);
            String result = null;
            try {
                while (System.currentTimeMillis() < deadline) {
                    Optional<Map<String, String>> row = storageGateway.findPendingCommand(corrId);
                    if (row.isPresent() && row.get().get("result") != null) {
                        result = row.get().get("result");
                        break;
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                storageGateway.deletePendingCommand(corrId);
                log.severe("[IDFS] Command interrupted: " + e.getMessage());
                writeJson(ex, 500, Map.of("error", "Command interrupted"));
                return;
            }

            storageGateway.deletePendingCommand(corrId);

            if (result == null) {
                log.warning("[IDFS] Command timed out: deviceId=" + deviceId
                        + " command=" + commandType + " correlationId=" + corrId);
                writeJson(ex, 504, Map.of(
                        "acknowledged",   false,
                        "correlation_id", corrId,
                        "error", "Device did not acknowledge within " + COMMAND_TIMEOUT_SECONDS + "s"));
            } else if ("SUCCESS".equals(result)) {
                log.info("[IDFS] Command acknowledged: deviceId=" + deviceId
                        + " command=" + commandType);
                writeJson(ex, 200, Map.of(
                        "acknowledged",   true,
                        "correlation_id", corrId,
                        "device_id",      deviceId,
                        "command_type",   commandType));
            } else {
                log.warning("[IDFS] Command failed on device: deviceId=" + deviceId
                        + " command=" + commandType);
                writeJson(ex, 200, Map.of(
                        "acknowledged",   false,
                        "correlation_id", corrId,
                        "device_id",      deviceId,
                        "command_type",   commandType,
                        "error",          "Device reported failure"));
            }

        } catch (MqttException e) {
            log.severe("[IDFS] MQTT publish error: " + e.getMessage());
            writeJson(ex, 503, Map.of("error", "Failed to publish command to MQTT"));
        } catch (Exception e) {
            log.severe("[IDFS] Command dispatch error: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", "Internal Server Error"));
        }
    }

    // =====================================================================
    // POST /register
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

            String deviceId          = (String) body.get("device_id");
            String registrationToken = (String) body.get("registration_token");

            if (isBlank(deviceId) || isBlank(registrationToken)) {
                writeJson(ex, 400, Map.of("error",
                        "device_id and registration_token are required"));
                return;
            }

            log.info("[IDFS] Bootstrap registration request: deviceId=" + deviceId);

            ServiceResponse dmsResponse = httpClient.post(
                    dmsBaseUrl + "/dms/devices/accept-registration",
                    Map.of("deviceId", deviceId, "registrationToken", registrationToken));

            if (dmsResponse.isSuccess()) {
                Map dmsResult    = JsonUtil.fromJson(dmsResponse.body(), Map.class);
                Map regInfo      = (Map) dmsResult.get("registrationInfo");
                Map fwAssignment = (Map) dmsResult.get("firmwareAssignment");

                Map<String, String> deviceResponse = Map.of(
                        "device_id",         (String) regInfo.get("deviceId"),
                        "device_type",       (String) regInfo.get("deviceType"),
                        "registered_at",     (String) regInfo.get("registeredAt"),
                        "firmware_version",  (String) fwAssignment.get("firmwareVersion"),
                        "firmware_url",      (String) fwAssignment.get("firmwareUrl"),
                        "firmware_issued_at",(String) fwAssignment.get("issuedAt")
                );

                log.info("[IDFS] Bootstrap registration succeeded: deviceId=" + deviceId
                        + " firmwareVersion=" + fwAssignment.get("firmwareVersion"));
                writeJson(ex, 200, deviceResponse);
            } else {
                log.warning("[IDFS] DMS returned " + dmsResponse.statusCode()
                        + " for bootstrap deviceId=" + deviceId
                        + " body=" + dmsResponse.body());
                writeRaw(ex, dmsResponse.statusCode(), dmsResponse.body());
            }
        } catch (Exception e) {
            log.severe("[IDFS] Bootstrap registration error: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", "Internal Server Error"));
        }
    }

    // =====================================================================
    // POST /provision
    // =====================================================================

    private void handleRuntimeProvision(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            String requestBody = readBodyString(ex);
            Map body   = JsonUtil.fromJson(requestBody, Map.class);
            String deviceId = (String) body.get("device_id");

            if (isBlank(deviceId)) {
                writeJson(ex, 400, Map.of("error", "device_id is required"));
                return;
            }

            log.info("[IDFS] Runtime provisioning request: deviceId=" + deviceId);

            ServiceResponse dmsResponse = httpClient.get(
                    dmsBaseUrl + "/dms/devices/get?deviceId=" + deviceId);

            if (!dmsResponse.isSuccess()) {
                log.warning("[IDFS] Provisioning failed: device not found deviceId=" + deviceId);
                writeJson(ex, dmsResponse.statusCode(),
                        Map.of("error", "Device not found or not registered"));
                return;
            }

            String mqttClientId = "securenet-device-" + deviceId;
            String mqttUsername = "device-" + deviceId;
            String mqttPassword = "mqtt-pass-" + deviceId + "-" + System.currentTimeMillis();

            Map<String, String> credentials = Map.of(
                    "device_id",       deviceId,
                    "mqtt_broker_url", mqttBrokerUrl,
                    "mqtt_client_id",  mqttClientId,
                    "mqtt_username",   mqttUsername,
                    "mqtt_password",   mqttPassword
            );

            log.info("[IDFS] Runtime provisioning succeeded: deviceId=" + deviceId
                    + " mqttClientId=" + mqttClientId);
            writeJson(ex, 200, credentials);
        } catch (Exception e) {
            log.severe("[IDFS] Runtime provisioning error: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", "Internal Server Error"));
        }
    }

    // =====================================================================
    // POST /heartbeat
    // =====================================================================

    private void handleHeartbeat(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            String requestBody = readBodyString(ex);
            Map body   = JsonUtil.fromJson(requestBody, Map.class);
            String deviceId = (String) body.get("device_id");

            if (isBlank(deviceId)) {
                writeJson(ex, 400, Map.of("error", "device_id is required"));
                return;
            }

            log.fine("[IDFS] Heartbeat relay: deviceId=" + deviceId);

            ServiceResponse dmsResponse = httpClient.post(
                    dmsBaseUrl + "/dms/devices/heartbeat",
                    Map.of("deviceId", deviceId));

            writeRaw(ex, dmsResponse.statusCode(), dmsResponse.body());
        } catch (Exception e) {
            log.severe("[IDFS] Heartbeat error: " + e.getMessage());
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
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void writeRaw(HttpExchange ex, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}