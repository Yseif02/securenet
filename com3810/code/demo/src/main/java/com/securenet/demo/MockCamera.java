package com.securenet.demo;

import com.securenet.common.JsonUtil;
import com.securenet.model.DeviceType;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mock camera device with MQTT-based chunk streaming and durable local buffering.
 *
 * <h3>Chunk streaming protocol</h3>
 * <ol>
 *   <li>On STREAM_START, the camera receives a {@code session_id} and
 *       {@code vss_url} (which IDFS needs to know which VSS instance owns
 *       the session). The camera starts publishing chunks over MQTT to
 *       {@code securenet/devices/{id}/stream/chunks}, including {@code vss_url}
 *       only on the first chunk of the session.</li>
 *   <li>Each chunk is kept in a local {@code localChunkBuffer} (TreeMap keyed
 *       by sequence number) until acked.</li>
 *   <li>IDFS receives the chunks, batches them, flushes to VSS, then publishes
 *       an ack on {@code securenet/devices/{id}/stream/acks} with
 *       {@code acked_through: N}.</li>
 *   <li>On receiving the ack, the camera deletes chunks 0..N from its local
 *       buffer.</li>
 * </ol>
 *
 * <p>The camera has no knowledge of VSS. If IDFS or VSS fails, IDFS handles
 * the resume handshake transparently. The camera just keeps publishing chunks
 * over MQTT and waiting for acks.
 */
public class MockCamera extends AbstractMockDevice {

    private static final int MOTION_EVENT_INTERVAL = 3;
    private static final int CHUNK_INTERVAL_MS     = 500;

    private final AtomicBoolean streaming                  = new AtomicBoolean(false);
    private final AtomicReference<String> activeSessionId  = new AtomicReference<>(null);
    private final AtomicReference<String> activeVssUrl     = new AtomicReference<>(null);

    /**
     * Local chunk buffer. Chunks are kept here until IDFS acks them
     * via MQTT stream ack. Key = sequence number.
     */
    private final TreeMap<Long, byte[]> localChunkBuffer = new TreeMap<>();
    private final Object bufferLock = new Object();

    /** Highest sequence number acked by IDFS. -1 = nothing acked yet. */
    private final AtomicLong lastAckedSeq = new AtomicLong(-1L);

    /** Whether this is the first chunk of the stream (vss_url must be included). */
    private final AtomicBoolean firstChunk = new AtomicBoolean(true);

    public MockCamera(String deviceId, String registrationToken, String idfsBaseUrl) {
        super(deviceId, registrationToken, DeviceType.CAMERA, idfsBaseUrl);
    }

    public boolean isStreaming() { return streaming.get(); }

    // =====================================================================
    // Steady state
    // =====================================================================

    @Override
    protected void onSteadyStateTick(String tag, int heartbeatCount) {
        if (heartbeatCount > 0 && heartbeatCount % MOTION_EVENT_INTERVAL == 0) {
            long nonce = nextNonce();
            publishEvent("motion", Map.of(
                    "device_id",       getDeviceId(),
                    "event_type",      "MOTION_DETECTED",
                    "motion_detected", true,
                    "occurred_at_ms",  System.currentTimeMillis(),
                    "nonce",           nonce
            ));
            System.out.println(tag + " Published MOTION_DETECTED nonce=" + nonce);
        }

        if (streaming.get() && heartbeatCount % 2 == 0) {
            System.out.println(tag + " Streaming video chunks via MQTT...");
        }
    }

    // =====================================================================
    // MQTT command handling
    // =====================================================================

    @Override
    protected void onCommandReceived(String tag, String commandType, String correlationId) {
        handleCommand(tag, commandType, correlationId, null, null);
    }

    @Override
    public void onFullCommandReceived(String tag, Map cmd) {
        handleCommand(tag,
                (String) cmd.get("command_type"),
                (String) cmd.get("correlation_id"),
                (String) cmd.get("session_id"),
                (String) cmd.get("vss_url"));
    }

    private void handleCommand(String tag, String commandType, String correlationId,
                               String sessionId, String vssUrl) {
        if (commandType == null) {
            publishAck(correlationId, "UNKNOWN", false);
            return;
        }

        switch (commandType) {
            case "STREAM_START" -> {
                if (streaming.compareAndSet(false, true)) {
                    activeSessionId.set(sessionId);
                    activeVssUrl.set(vssUrl);
                    lastAckedSeq.set(-1L);
                    firstChunk.set(true);
                    synchronized (bufferLock) { localChunkBuffer.clear(); }

                    System.out.println(tag + " STREAM_START: sessionId=" + sessionId
                            + " vssUrl=" + vssUrl);

                    // Subscribe to stream acks from IDFS
                    subscribeToStreamAcks(tag);

                    publishEvent("stream", Map.of(
                            "device_id",      getDeviceId(),
                            "event_type",     "STREAM_STARTED",
                            "occurred_at_ms", System.currentTimeMillis(),
                            "nonce",          nextNonce()
                    ));
                    publishAck(correlationId, commandType, true);

                    if (sessionId != null) {
                        startChunkSender(tag, sessionId, vssUrl);
                    }
                } else {
                    System.out.println(tag + " Already streaming, acking duplicate");
                    publishAck(correlationId, commandType, true);
                }
            }

            case "STREAM_STOP" -> {
                if (streaming.compareAndSet(true, false)) {
                    System.out.println(tag + " STREAM_STOP received");
                    activeSessionId.set(null);
                    activeVssUrl.set(null);

                    publishEvent("stream", Map.of(
                            "device_id",      getDeviceId(),
                            "event_type",     "STREAM_ENDED",
                            "occurred_at_ms", System.currentTimeMillis(),
                            "nonce",          nextNonce()
                    ));
                    publishAck(correlationId, commandType, true);
                } else {
                    System.out.println(tag + " Not streaming, acking anyway");
                    publishAck(correlationId, commandType, true);
                }
            }

            default -> {
                System.out.println(tag + " Unknown command: " + commandType);
                publishAck(correlationId, commandType, false);
            }
        }
    }

    // =====================================================================
    // MQTT stream ack subscription
    // =====================================================================

    /**
     * Subscribes to IDFS stream acks on
     * {@code securenet/devices/{id}/stream/acks}.
     *
     * <p>When IDFS successfully flushes a batch to VSS it publishes:
     * <pre>{ "session_id": "rec-...", "acked_through": 19 }</pre>
     * The camera prunes its local buffer up to seq 19.
     */
    private void subscribeToStreamAcks(String tag) {
        if (mqttClient == null || !mqttClient.isConnected()) return;
        try {
            String ackTopic = "securenet/devices/" + getDeviceId() + "/stream/acks";
            mqttClient.subscribe(ackTopic, 1, (topic, message) -> {
                try {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    Map ack        = JsonUtil.fromJson(payload, Map.class);
                    String sid     = (String) ack.get("session_id");
                    long ackedThrough = ((Number) ack.get("acked_through")).longValue();

                    if (!getDeviceId().equals(extractDeviceId()) ||
                            !sid.equals(activeSessionId.get())) {
                        return; // stale ack from a previous session
                    }

                    pruneLocalBuffer(ackedThrough);
                    lastAckedSeq.set(ackedThrough);
                    System.out.println(tag + " Stream ack received: ackedThrough=" + ackedThrough
                            + " localBufferSize=" + localChunkBufferSize());
                } catch (Exception e) {
                    System.err.println(tag + " Error processing stream ack: " + e.getMessage());
                }
            });
            System.out.println(tag + " Subscribed to stream acks on " + ackTopic);
        } catch (MqttException e) {
            System.err.println(tag + " Failed to subscribe to stream acks: " + e.getMessage());
        }
    }

    // =====================================================================
    // MQTT chunk sender
    // =====================================================================

    /**
     * Publishes simulated video chunks over MQTT to
     * {@code securenet/devices/{id}/stream/chunks}.
     *
     * <p>Chunks are buffered locally until IDFS sends an ack. The first
     * chunk of the session includes {@code vss_url} so IDFS knows which
     * VSS instance DMS assigned to this session.
     */
    private void startChunkSender(String tag, String sessionId, String vssUrl) {
        Thread sender = new Thread(() -> {
            System.out.println(tag + " Chunk sender started: sessionId=" + sessionId);
            long seq = 0;

            while (streaming.get()) {
                try {
                    byte[] frameData = ("FRAME_" + getDeviceId() + "_"
                            + seq + "_" + System.currentTimeMillis())
                            .getBytes(StandardCharsets.UTF_8);
                    String b64 = java.util.Base64.getEncoder().encodeToString(frameData);

                    // Buffer locally before publishing
                    synchronized (bufferLock) {
                        localChunkBuffer.put(seq, frameData);
                    }

                    // Build the MQTT payload
                    // vss_url is only included on the first chunk so IDFS
                    // knows which VSS instance owns this session.
                    // Subsequent chunks omit it to save bandwidth.
                    java.util.Map<String, Object> chunkPayload = new java.util.HashMap<>();
                    chunkPayload.put("session_id",      sessionId);
                    chunkPayload.put("sequence_number", seq);
                    chunkPayload.put("chunk_bytes",     b64);
                    if (firstChunk.getAndSet(false)) {
                        chunkPayload.put("vss_url", vssUrl);
                        System.out.println(tag + " First chunk — including vss_url=" + vssUrl);
                    }

                    String topic   = "securenet/devices/" + getDeviceId() + "/stream/chunks";
                    String payload = JsonUtil.toJson(chunkPayload);

                    if (mqttClient != null && mqttClient.isConnected()) {
                        mqttClient.publish(topic,
                                payload.getBytes(StandardCharsets.UTF_8), 1, false);
                        System.out.println(tag + " Chunk published via MQTT: seq=" + seq
                                + " bytes=" + frameData.length
                                + " localBuffer=" + localChunkBufferSize());
                    } else {
                        System.err.println(tag + " MQTT not connected, chunk seq=" + seq
                                + " remains in local buffer");
                    }

                    seq++;
                    Thread.sleep(CHUNK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (MqttException e) {
                    System.err.println(tag + " MQTT publish error seq=" + seq
                            + ": " + e.getMessage());
                    // Chunk stays in local buffer — MQTT auto-reconnects
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                } catch (Exception e) {
                    System.err.println(tag + " Chunk sender error seq=" + seq
                            + ": " + e.getMessage());
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                }
            }
            System.out.println(tag + " Chunk sender stopped after " + seq + " chunks."
                    + " Unacked chunks in buffer: " + localChunkBufferSize());
        }, "chunk-sender-" + getDeviceId());
        sender.setDaemon(true);
        sender.start();
    }

    // =====================================================================
    // Local buffer helpers
    // =====================================================================

    private void pruneLocalBuffer(long ackedThrough) {
        synchronized (bufferLock) {
            // headMap(key, inclusive=true) → all keys <= ackedThrough
            localChunkBuffer.headMap(ackedThrough, true).clear();
        }
    }

    private int localChunkBufferSize() {
        synchronized (bufferLock) { return localChunkBuffer.size(); }
    }

    /** Extracts deviceId — used for ack filtering. Returns this device's ID. */
    private String extractDeviceId() { return getDeviceId(); }
}