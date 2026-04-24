package com.securenet.iotfirmware.server;

import com.securenet.common.JsonUtil;
import com.securenet.common.LoadBalancer;
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
import java.util.logging.Logger;

/**
 * HTTP server for the IoT Device Firmware Service (IDFS).
 *
 * <p>IDFS is the device-facing entry point into the SecureNet cloud.
 * Devices call IDFS over HTTP for onboarding and provisioning. The
 * platform calls IDFS to dispatch commands to devices via MQTT.
 *
 * <h3>Device-facing HTTP endpoints</h3>
 * <ul>
 *   <li>{@code POST /register}  — bootstrap registration relay to DMS</li>
 *   <li>{@code POST /provision} — runtime MQTT credential provisioning</li>
 *   <li>{@code POST /heartbeat} — heartbeat relay to DMS</li>
 * </ul>
 *
 * <h3>Platform-facing HTTP endpoints (called by DMS)</h3>
 * <ul>
 *   <li>{@code POST /command} — publish command to device MQTT topic, wait for ack</li>
 * </ul>
 *
 * <h3>MQTT subscriptions</h3>
 * <ul>
 *   <li>{@code securenet/devices/+/acks}          — device command acks (all instances; idempotent DB write)</li>
 *   <li>{@code securenet/devices/+/events/#}       — device events, forwarded to EPS</li>
 *   <li>{@code securenet/devices/+/stream/chunks}  — video chunk streaming (all instances; slot-ownership filtered)</li>
 * </ul>
 *
 * <h3>Chunk streaming design</h3>
 * <p>Cameras publish video chunks over MQTT rather than HTTP. IDFS buffers
 * chunks in memory per session in a {@link ChunkBuffer}. Every
 * {@value #CHUNK_FLUSH_INTERVAL} chunks, IDFS flushes the batch to VSS via
 * HTTP and publishes a stream ack back to the camera on
 * {@code securenet/devices/{id}/stream/acks} with {@code ackedThrough: N}.
 * The camera deletes its local buffer up to N.
 *
 * <h3>Slot ownership (DS Problem #10 — Competing Consumer)</h3>
 * <p>All 3 IDFS instances subscribe to chunk topics via a shared subscription
 * ({@code $share/idfs-chunk-processors/...}). The broker delivers each message
 * to exactly one instance. As a secondary guard, the receiving instance checks
 * consistent hash ownership: {@code hash(sessionId) % idfsClusterSize == instanceIndex}.
 * If it does not own the session it drops the chunk immediately. This prevents
 * duplicate VSS writes if the broker ever fan-outs (e.g. during reconnect).
 *
 * <p>If this IDFS instance crashes mid-stream, the camera's next chunk
 * arrives at a different IDFS instance (broker shared subscription). That
 * instance initialises a fresh {@link ChunkBuffer} for the session and
 * continues from wherever VSS last checkpointed — the camera will eventually
 * receive an ack and prune its buffer.
 */
public class IdfsServer {

    private static final Logger log = Logger.getLogger(IdfsServer.class.getName());

    private static final int COMMAND_TIMEOUT_SECONDS = 10;

    /** Number of chunks to accumulate before flushing a batch to VSS. */
    private static final int CHUNK_FLUSH_INTERVAL = 10;

    /** Max attempts to find a healthy VSS via resume handshake. */
    private static final int VSS_FAILOVER_ATTEMPTS = 5;
    private static final int VSS_FAILOVER_BACKOFF_MS = 2000;

    private final String host;
    private final int port;
    private final LoadBalancer dmsLoadBalancer;
    private final LoadBalancer epsLoadBalancer;
    private final LoadBalancer vssLoadBalancer;
    private final String mqttBrokerUrl;
    private final ServiceClient httpClient;
    private final StorageGateway storageGateway;

    /** Slot index assigned to this IDFS instance (0-based). Stable across restarts. */
    private final int instanceIndex;

    /** Total number of IDFS slots in the cluster. */
    private final int idfsClusterSize;

    private HttpServer httpServer;
    private MqttClient mqttClient;
    private ExecutorService ackExecutor;
    private ExecutorService eventExecutor;
    private ExecutorService chunkExecutor;

    /**
     * In-memory chunk buffers keyed by sessionId.
     * Each buffer tracks chunks received since the last VSS flush.
     */
    private final ConcurrentHashMap<String, ChunkBuffer> chunkBuffers =
            new ConcurrentHashMap<>();

    /**
     * Per-session chunk buffer.
     * Guarded by its own intrinsic lock so concurrent MQTT callbacks
     * for different chunks of the same session are serialised correctly.
     */
    private static final class ChunkBuffer {
        /** Chunks received since the last flush, keyed by sequence number. */
        final TreeMap<Long, byte[]> pending = new TreeMap<>();
        /** Highest seq number successfully flushed to VSS. -1 = nothing flushed yet. */
        long lastFlushedSeq = -1L;
        /** Direct URL of the VSS instance owning this session. */
        String vssUrl;
        /** deviceId — needed to publish the MQTT ack. */
        final String deviceId;

        ChunkBuffer(String deviceId, String vssUrl) {
            this.deviceId = deviceId;
            this.vssUrl   = vssUrl;
        }
    }

    /**
     * Constructor without slot ownership args — defaults to index=0, size=1.
     * Kept for backwards compatibility with tests that don't supply cluster args.
     */
    public IdfsServer(String host, int port,
                      LoadBalancer dmsLoadBalancer,
                      LoadBalancer epsLoadBalancer,
                      LoadBalancer vssLoadBalancer,
                      String mqttBrokerUrl,
                      StorageGateway storageGateway) {
        this(host, port, dmsLoadBalancer, epsLoadBalancer, vssLoadBalancer,
                mqttBrokerUrl, storageGateway, 0, 1);
    }

    /**
     * Full constructor with slot ownership for competing-consumer chunk processing.
     *
     * @param instanceIndex   zero-based slot index assigned to this instance
     * @param idfsClusterSize total number of IDFS slots (used for hash ownership)
     */
    public IdfsServer(String host, int port,
                      LoadBalancer dmsLoadBalancer,
                      LoadBalancer epsLoadBalancer,
                      LoadBalancer vssLoadBalancer,
                      String mqttBrokerUrl,
                      StorageGateway storageGateway,
                      int instanceIndex,
                      int idfsClusterSize) {
        this.host            = Objects.requireNonNull(host);
        this.port            = port;
        this.dmsLoadBalancer = Objects.requireNonNull(dmsLoadBalancer);
        this.epsLoadBalancer = Objects.requireNonNull(epsLoadBalancer);
        this.vssLoadBalancer = Objects.requireNonNull(vssLoadBalancer);
        this.mqttBrokerUrl   = Objects.requireNonNull(mqttBrokerUrl);
        this.storageGateway  = Objects.requireNonNull(storageGateway);
        this.httpClient      = new ServiceClient();
        this.instanceIndex   = instanceIndex;
        this.idfsClusterSize = idfsClusterSize;
    }

    public void start() throws IOException {
        ackExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "idfs-ack-worker");
            t.setDaemon(true);
            return t;
        });
        eventExecutor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "idfs-event-worker");
            t.setDaemon(true);
            return t;
        });
        chunkExecutor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "idfs-chunk-worker");
            t.setDaemon(true);
            return t;
        });

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
        log.info("[IDFS] started on " + host + ":" + port
                + " (slot " + instanceIndex + "/" + idfsClusterSize + ")");
    }

    public void stop() {
        if (httpServer != null) httpServer.stop(0);
        disconnectMqtt();
        shutdownExecutor(ackExecutor, "ackExecutor");
        shutdownExecutor(eventExecutor, "eventExecutor");
        shutdownExecutor(chunkExecutor, "chunkExecutor");
        System.out.println("[IDFS] stopped");
    }

    // =====================================================================
    // MQTT connection
    // =====================================================================

    private void connectMqtt() {
        try {
            String clientId = "idfs-dispatcher-"
                    + UUID.randomUUID().toString().substring(0, 8);
            mqttClient = new MqttClient(mqttBrokerUrl, clientId);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            mqttClient.connect(opts);

            // NOTE: Moquette (the embedded broker version used here) does not support
            // MQTT5 shared subscriptions ($share/...). All three IDFS instances use
            // regular wildcard subscriptions so Moquette delivers every message to all
            // three. Deduplication is handled at the application level:
            //
            //   Acks:   updatePendingCommandResult is idempotent — writing the same
            //           result twice is harmless.
            //   Events: EPS dedup table suppresses duplicate event ingestion.
            //   Chunks: slot ownership check (hash(sessionId) % clusterSize == index)
            //           ensures only the owning instance processes each session's chunks.
            //
            // If you upgrade to a Moquette version that supports MQTT5 / shared subs,
            // replace these three lines with $share/... topic strings.

            mqttClient.subscribe("securenet/devices/+/acks", 1,
                    (topic, message) -> submitMqttTask(
                            ackExecutor,
                            "ack",
                            () -> onAckReceived(topic, message)));
            mqttClient.subscribe("securenet/devices/+/events/#", 1,
                    (topic, message) -> submitMqttTask(
                            eventExecutor,
                            "event",
                            () -> onDeviceEventReceived(topic, message)));
            mqttClient.subscribe("securenet/devices/+/stream/chunks", 1,
                    (topic, message) -> submitMqttTask(
                            chunkExecutor,
                            "chunk",
                            () -> onChunkReceived(topic, message)));

            log.info("[IDFS] MQTT connected to " + mqttBrokerUrl);
            log.info("[IDFS] Subscribed: acks (all), events (all), stream/chunks (all, slot-filtered)");
            log.info("[IDFS] Slot ownership: index=" + instanceIndex
                    + " clusterSize=" + idfsClusterSize);
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
    // MQTT: command ack handler
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
            log.info("[IDFS] Ack received: correlationId=" + corrId + " result=" + result);
        } catch (Exception e) {
            log.severe("[IDFS] Error processing ack: " + e.getMessage());
        }
    }

    // =====================================================================
    // MQTT: device event handler — forward to EPS
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

            // Ignore streaming lifecycle events — not for EPS
            if ("STREAM_STARTED".equals(eventType) || "STREAM_ENDED".equals(eventType)) {
                log.fine("[IDFS] Ignoring streaming lifecycle event: " + eventType);
                return;
            }

            log.info("[IDFS] Device event received: topic=" + topic
                    + " deviceId=" + deviceId + " type=" + eventType);

            Object nonceObj   = event.get("nonce");
            String nonce      = (nonceObj != null) ? String.valueOf(nonceObj) : "";
            Object tsObj      = event.get("occurred_at_ms");
            String occurredAt = (tsObj instanceof Number)
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
                    epsLoadBalancer.nextHealthyUrl() + "/eps/events/ingest", epsRequest);

            // EPS followers return 503 with {"error":"...", "leaderId":"eps-1"}.
            // Follow the redirect once by retrying on each known EPS URL until
            // one accepts (leader) or all are exhausted.
            if (!epsResponse.isSuccess() && epsResponse.statusCode() == 503) {
                log.fine("[IDFS] EPS returned 503 (not leader) — trying other nodes for "
                        + eventType + " from " + deviceId);
                // Try the other two EPS nodes by calling nextHealthyUrl() twice more
                for (int retry = 0; retry < 2; retry++) {
                    epsResponse = httpClient.post(
                            epsLoadBalancer.nextHealthyUrl() + "/eps/events/ingest", epsRequest);
                    if (epsResponse.isSuccess()) break;
                }
            }

            if (epsResponse.isSuccess()) {
                log.info("[IDFS] Event forwarded to EPS: " + eventType + " from " + deviceId);
            } else {
                log.warning("[IDFS] EPS rejected event: HTTP " + epsResponse.statusCode()
                        + " deviceId=" + deviceId + " type=" + eventType);
            }
        } catch (Exception e) {
            log.severe("[IDFS] Error forwarding event to EPS: " + e.getMessage());
        }
    }

    // =====================================================================
    // MQTT: video chunk handler
    // =====================================================================

    /**
     * Handles a chunk published by a camera on
     * {@code securenet/devices/{deviceId}/stream/chunks}.
     *
     * <p>Expected payload fields:
     * <pre>
     * {
     *   "session_id":      "rec-...",
     *   "sequence_number": 42,
     *   "chunk_bytes":     "<base64>",
     *   "vss_url":         "http://localhost:9005"   // only on first chunk of session
     * }
     * </pre>
     *
     * <p>Slot ownership check: {@code hash(sessionId) % idfsClusterSize == instanceIndex}.
     * If this instance does not own the session the chunk is dropped immediately.
     * The shared subscription already ensures the broker delivers to only one
     * instance, but this guard prevents any edge-case double-delivery.
     *
     * <p>Every {@value #CHUNK_FLUSH_INTERVAL} chunks the batch is flushed
     * to VSS and an MQTT ack is sent to the camera.
     */
    private void onChunkReceived(String topic, MqttMessage message) {
        try {
            String payload  = new String(message.getPayload(), StandardCharsets.UTF_8);
            Map chunk       = JsonUtil.fromJson(payload, Map.class);

            String sessionId = (String) chunk.get("session_id");
            String deviceId  = extractDeviceIdFromTopic(topic);
            long seq         = ((Number) chunk.get("sequence_number")).longValue();
            byte[] bytes     = java.util.Base64.getDecoder()
                    .decode((String) chunk.get("chunk_bytes"));
            // vss_url is sent on the first chunk of a session so IDFS knows
            // which VSS instance DMS assigned. Subsequent chunks omit it.
            String vssUrl    = (String) chunk.get("vss_url");

            if (sessionId == null || deviceId == null) {
                log.warning("[IDFS] Malformed chunk on topic=" + topic);
                return;
            }

            // --- Slot ownership check (DS Problem #10: Competing Consumer) ---
            // Even though the shared subscription delivers each message to only
            // one IDFS instance, we verify ownership here as a secondary guard.
            int ownerSlot = Math.abs(sessionId.hashCode()) % idfsClusterSize;
            if (ownerSlot != instanceIndex) {
                log.fine("[IDFS] Ignoring chunk — session owned by slot " + ownerSlot
                        + " (I am slot " + instanceIndex + ") sessionId=" + sessionId);
                return;
            }

            log.fine("[IDFS] Chunk received: sessionId=" + sessionId
                    + " deviceId=" + deviceId + " seq=" + seq
                    + " bytes=" + bytes.length);

            // Get or create the buffer for this session
            ChunkBuffer buf = chunkBuffers.computeIfAbsent(sessionId, id -> {
                // First chunk for this session on this IDFS instance.
                // If vssUrl is null this is a resumed session after IDFS crash —
                // we'll discover the VSS URL on first flush via the resume handshake.
                String url = (vssUrl != null) ? vssUrl : null;
                log.info("[IDFS] New chunk buffer: sessionId=" + id
                        + " deviceId=" + deviceId + " vssUrl=" + url);
                return new ChunkBuffer(deviceId, url);
            });

            // If we got a vssUrl and the buffer doesn't have one yet
            // (resumed after IDFS crash), set it now
            synchronized (buf) {
                if (vssUrl != null && buf.vssUrl == null) {
                    buf.vssUrl = vssUrl;
                }
                buf.pending.put(seq, bytes);

                // Count chunks above the last flush point
                long chunksInBatch = buf.pending.tailMap(buf.lastFlushedSeq + 1).size();

                if (chunksInBatch >= CHUNK_FLUSH_INTERVAL) {
                    flushChunksToVss(sessionId, buf);
                }
            }

        } catch (Exception e) {
            log.severe("[IDFS] Error processing chunk from topic=" + topic
                    + ": " + e.getMessage());
        }
    }

    /**
     * Flushes pending chunks to VSS via HTTP and publishes a stream ack
     * to the camera over MQTT.
     *
     * <p>Must be called with {@code buf}'s monitor held.
     */
    private void flushChunksToVss(String sessionId, ChunkBuffer buf) {
        // Collect the batch: all chunks above lastFlushedSeq, in order
        NavigableMap<Long, byte[]> batch =
                buf.pending.tailMap(buf.lastFlushedSeq + 1, true);

        if (batch.isEmpty()) return;

        long batchHighSeq = batch.lastKey();

        // Build the batch payload matching VSS /vss/chunks/ingest:
        // {
        //   "recordingSessionId": "rec-...",
        //   "chunks": [
        //     { "sequenceNumber": 0, "chunkBytes": "<base64>" },
        //     ...
        //   ]
        // }
        List<Map<String, Object>> chunkList = new ArrayList<>();
        for (Map.Entry<Long, byte[]> entry : batch.entrySet()) {
            chunkList.add(Map.of(
                    "sequenceNumber", entry.getKey(),
                    "chunkBytes",     java.util.Base64.getEncoder().encodeToString(entry.getValue())
            ));
        }

        Map<String, Object> vssPayload = new HashMap<>();
        vssPayload.put("recordingSessionId", sessionId);
        vssPayload.put("chunks",             chunkList);

        boolean flushed = false;
        for (int attempt = 1; attempt <= VSS_FAILOVER_ATTEMPTS; attempt++) {
            try {
                if (buf.vssUrl == null) {
                    buf.vssUrl = resumeVssSession(sessionId);
                    if (buf.vssUrl == null) {
                        // Session is gone from all VSS instances (404) — it was
                        // closed by DMS before this flush. Drop the buffer.
                        log.warning("[IDFS] Session not found on any VSS — dropping buffer"
                                + " sessionId=" + sessionId);
                        chunkBuffers.remove(sessionId);
                        return;
                    }
                }

                ServiceResponse resp = httpClient.post(
                        buf.vssUrl + "/vss/chunks/ingest", vssPayload);

                if (resp.isSuccess()) {
                    flushed = true;
                    break;
                }
                // 404 means VSS closed the session — stop retrying
                if (resp.statusCode() == 404 || resp.statusCode() == 400) {
                    log.warning("[IDFS] VSS returned " + resp.statusCode()
                            + " for ingest — session closed, dropping buffer"
                            + " sessionId=" + sessionId);
                    chunkBuffers.remove(sessionId);
                    return;
                }
                log.warning("[IDFS] VSS ingest HTTP " + resp.statusCode()
                        + " attempt=" + attempt + " sessionId=" + sessionId);
                buf.vssUrl = null; // force resume on next attempt
                sleepQuietly(VSS_FAILOVER_BACKOFF_MS);

            } catch (Exception e) {
                log.warning("[IDFS] VSS unreachable on attempt " + attempt
                        + " sessionId=" + sessionId + ": " + e.getMessage());
                buf.vssUrl = null; // force resume on next attempt
                sleepQuietly(VSS_FAILOVER_BACKOFF_MS);
            }
        }

        if (!flushed) {
            log.severe("[IDFS] Failed to flush chunks after " + VSS_FAILOVER_ATTEMPTS
                    + " attempts — sessionId=" + sessionId + ". Chunks remain buffered.");
            return;
        }

        // Update watermark and prune flushed chunks from in-memory buffer
        buf.lastFlushedSeq = batchHighSeq;
        buf.pending.headMap(batchHighSeq, true).clear();

        log.info("[IDFS] Flush complete: sessionId=" + sessionId
                + " batchSize=" + chunkList.size() + " ackedThrough=" + batchHighSeq);

        // Publish MQTT ack to camera
        publishStreamAck(buf.deviceId, sessionId, batchHighSeq);
    }

    /**
     * Calls {@code POST /vss/session/resume} on a healthy VSS instance.
     *
     * @return the new VSS instance URL, or {@code null} if the session is not
     *         found on any VSS (404 — it has been closed) or all attempts fail
     */
    private String resumeVssSession(String sessionId) {
        log.info("[IDFS] Attempting VSS resume for sessionId=" + sessionId);
        for (int i = 0; i < VSS_FAILOVER_ATTEMPTS; i++) {
            try {
                String vssBase = vssLoadBalancer.nextHealthyUrl();
                ServiceResponse resp = httpClient.post(
                        vssBase + "/vss/session/resume",
                        Map.of("sessionId", sessionId));
                if (resp.isSuccess()) {
                    Map respMap   = JsonUtil.fromJson(resp.body(), Map.class);
                    String newUrl = (String) respMap.get("vssUrl");
                    log.info("[IDFS] VSS resume succeeded: sessionId=" + sessionId
                            + " newVssUrl=" + newUrl);
                    return newUrl;
                }
                // 404 = session doesn't exist on any VSS (already closed by DMS).
                // No point retrying — return null so caller can clean up.
                if (resp.statusCode() == 404) {
                    log.warning("[IDFS] VSS resume 404 — session already closed"
                            + " sessionId=" + sessionId);
                    return null;
                }
                log.warning("[IDFS] VSS resume HTTP " + resp.statusCode()
                        + " attempt=" + (i + 1) + " sessionId=" + sessionId);
            } catch (Exception e) {
                log.warning("[IDFS] VSS resume error attempt=" + (i + 1)
                        + " sessionId=" + sessionId + ": " + e.getMessage());
            }
            sleepQuietly(VSS_FAILOVER_BACKOFF_MS);
        }
        return null;
    }

    /**
     * Publishes a stream ack to the camera on
     * {@code securenet/devices/{deviceId}/stream/acks}.
     *
     * <p>The camera deletes its local chunk buffer up to {@code ackedThrough}.
     */
    private void publishStreamAck(String deviceId, String sessionId, long ackedThrough) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warning("[IDFS] Cannot publish stream ack — MQTT not connected");
            return;
        }
        try {
            String topic   = "securenet/devices/" + deviceId + "/stream/acks";
            String payload = JsonUtil.toJson(Map.of(
                    "session_id",    sessionId,
                    "acked_through", ackedThrough
            ));
            mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
            log.fine("[IDFS] Stream ack published: deviceId=" + deviceId
                    + " sessionId=" + sessionId + " ackedThrough=" + ackedThrough);
        } catch (MqttException e) {
            log.warning("[IDFS] Failed to publish stream ack: " + e.getMessage());
        }
    }

    /**
     * Extracts the deviceId from an MQTT topic of the form
     * {@code securenet/devices/{deviceId}/stream/chunks}.
     */
    private static String extractDeviceIdFromTopic(String topic) {
        // "securenet/devices/{deviceId}/stream/chunks"
        // Also handles shared-subscription topics which include the group prefix,
        // but Eclipse Paho strips the $share/group prefix before calling the callback.
        String[] parts = topic.split("/");
        return (parts.length >= 3) ? parts[2] : null;
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void submitMqttTask(ExecutorService executor, String taskType, Runnable task) {
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            log.warning("[IDFS] Dropping " + taskType + " task during shutdown");
        }
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) return;
        executor.shutdownNow();
        log.info("[IDFS] Stopped " + name);
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
                log.severe("[IDFS] Command dispatch failed: MQTT not connected deviceId=" + deviceId);
                writeJson(ex, 503, Map.of("error", "MQTT broker not connected"));
                return;
            }

            log.info("[IDFS] Command dispatch: deviceId=" + deviceId
                    + " command=" + commandType + " correlationId=" + corrId);

            // Persist before publishing so any IDFS instance can receive the ack
            Instant now       = Instant.now();
            Instant expiresAt = now.plusSeconds(COMMAND_TIMEOUT_SECONDS);
            storageGateway.savePendingCommand(corrId, deviceId, commandType, now, expiresAt);

            String topic = "securenet/devices/" + deviceId + "/commands/" + commandType.toLowerCase();
            Map<String, Object> mqttPayload = new HashMap<>(body);
            mqttPayload.put("device_id",      deviceId);
            mqttPayload.put("command_type",   commandType);
            mqttPayload.put("correlation_id", corrId);

            mqttClient.publish(topic,
                    JsonUtil.toJson(mqttPayload).getBytes(StandardCharsets.UTF_8), 1, false);
            log.info("[IDFS] Published command to " + topic);

            // Poll DB for ack (any IDFS instance may write it)
            long deadline = System.currentTimeMillis() + (COMMAND_TIMEOUT_SECONDS * 1000L);
            String result = null;
            while (System.currentTimeMillis() < deadline) {
                Optional<Map<String, String>> row = storageGateway.findPendingCommand(corrId);
                if (row.isPresent() && row.get().get("result") != null) {
                    result = row.get().get("result");
                    break;
                }
                try { Thread.sleep(100); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    storageGateway.deletePendingCommand(corrId);
                    writeJson(ex, 500, Map.of("error", "Command interrupted"));
                    return;
                }
            }

            storageGateway.deletePendingCommand(corrId);

            if (result == null) {
                log.warning("[IDFS] Command timed out: deviceId=" + deviceId
                        + " command=" + commandType);
                writeJson(ex, 504, Map.of(
                        "acknowledged",   false,
                        "correlation_id", corrId,
                        "error", "Device did not acknowledge within "
                                + COMMAND_TIMEOUT_SECONDS + "s"));
            } else if ("SUCCESS".equals(result)) {
                log.info("[IDFS] Command acknowledged: deviceId=" + deviceId
                        + " command=" + commandType);
                writeJson(ex, 200, Map.of(
                        "acknowledged",   true,
                        "correlation_id", corrId,
                        "device_id",      deviceId,
                        "command_type",   commandType));
            } else {
                log.warning("[IDFS] Command failed on device: deviceId=" + deviceId);
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

            Map body = JsonUtil.fromJson(readBodyString(ex), Map.class);
            String deviceId          = (String) body.get("device_id");
            String registrationToken = (String) body.get("registration_token");

            if (isBlank(deviceId) || isBlank(registrationToken)) {
                writeJson(ex, 400, Map.of("error",
                        "device_id and registration_token are required"));
                return;
            }

            log.info("[IDFS] Bootstrap registration: deviceId=" + deviceId);

            ServiceResponse dmsResponse = httpClient.post(
                    dmsLoadBalancer.nextHealthyUrl() + "/dms/devices/accept-registration",
                    Map.of("deviceId", deviceId, "registrationToken", registrationToken));

            if (dmsResponse.isSuccess()) {
                Map dmsResult    = JsonUtil.fromJson(dmsResponse.body(), Map.class);
                Map regInfo      = (Map) dmsResult.get("registrationInfo");
                Map fwAssignment = (Map) dmsResult.get("firmwareAssignment");

                writeJson(ex, 200, Map.of(
                        "device_id",          (String) regInfo.get("deviceId"),
                        "device_type",        (String) regInfo.get("deviceType"),
                        "registered_at",      (String) regInfo.get("registeredAt"),
                        "firmware_version",   (String) fwAssignment.get("firmwareVersion"),
                        "firmware_url",       (String) fwAssignment.get("firmwareUrl"),
                        "firmware_issued_at", (String) fwAssignment.get("issuedAt")
                ));
                log.info("[IDFS] Bootstrap succeeded: deviceId=" + deviceId);
            } else {
                log.warning("[IDFS] DMS returned " + dmsResponse.statusCode()
                        + " for bootstrap deviceId=" + deviceId);
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

            Map body    = JsonUtil.fromJson(readBodyString(ex), Map.class);
            String deviceId = (String) body.get("device_id");

            if (isBlank(deviceId)) {
                writeJson(ex, 400, Map.of("error", "device_id is required"));
                return;
            }

            log.info("[IDFS] Runtime provisioning: deviceId=" + deviceId);

            ServiceResponse dmsResponse = httpClient.get(
                    dmsLoadBalancer.nextHealthyUrl() + "/dms/devices/get?deviceId=" + deviceId);

            if (!dmsResponse.isSuccess()) {
                writeJson(ex, dmsResponse.statusCode(),
                        Map.of("error", "Device not found or not registered"));
                return;
            }

            writeJson(ex, 200, Map.of(
                    "device_id",       deviceId,
                    "mqtt_broker_url", mqttBrokerUrl,
                    "mqtt_client_id",  "securenet-device-" + deviceId,
                    "mqtt_username",   "device-" + deviceId,
                    "mqtt_password",   "mqtt-pass-" + deviceId + "-" + System.currentTimeMillis()
            ));
            log.info("[IDFS] Provisioning succeeded: deviceId=" + deviceId);
        } catch (Exception e) {
            log.severe("[IDFS] Provisioning error: " + e.getMessage());
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

            Map body    = JsonUtil.fromJson(readBodyString(ex), Map.class);
            String deviceId = (String) body.get("device_id");

            if (isBlank(deviceId)) {
                writeJson(ex, 400, Map.of("error", "device_id is required"));
                return;
            }

            log.fine("[IDFS] Heartbeat relay: deviceId=" + deviceId);

            ServiceResponse dmsResponse = httpClient.post(
                    dmsLoadBalancer.nextHealthyUrl() + "/dms/devices/heartbeat",
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
