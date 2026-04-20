package com.securenet.demo;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.model.DeviceType;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mock camera device.
 *
 * <p>Combines motion detection (like {@link MockSensor}) with video
 * streaming capability. Publishes periodic {@code MOTION_DETECTED}
 * events and responds to {@code STREAM_START} / {@code STREAM_STOP}
 * commands.
 *
 * <p>On {@code STREAM_START}, the camera receives a {@code session_id}
 * and {@code vss_url} in the command payload. It then sends simulated
 * video chunks directly to the Video Streaming Service via
 * {@code POST /vss/chunks/ingest} until {@code STREAM_STOP} is received.
 */
public class MockCamera extends AbstractMockDevice {

    private static final int MOTION_EVENT_INTERVAL = 3;
    private static final int CHUNK_INTERVAL_MS     = 500;

    private final AtomicBoolean streaming        = new AtomicBoolean(false);
    private final AtomicReference<String> activeSessionId = new AtomicReference<>(null);
    private final AtomicReference<String> activeVssUrl    = new AtomicReference<>(null);
    private final AtomicLong chunkSequence       = new AtomicLong(0);

    public MockCamera(String deviceId, String registrationToken, String idfsBaseUrl) {
        super(deviceId, registrationToken, DeviceType.CAMERA, idfsBaseUrl);
    }

    public boolean isStreaming() { return streaming.get(); }

    @Override
    protected void onSteadyStateTick(String tag, int heartbeatCount) {
        // Publish motion events periodically
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
            System.out.println(tag + " Streaming video chunks to VSS...");
        }
    }

    /**
     * Called by {@link AbstractMockDevice} when an MQTT command arrives.
     * For STREAM_START, the full payload including session_id and vss_url
     * is extracted via {@link #onFullCommandReceived}.
     */
    @Override
    protected void onCommandReceived(String tag, String commandType, String correlationId) {
        // session_id / vss_url are set by onFullCommandReceived before this
        // is called — see the override below. For commands that don't need
        // extra fields (STREAM_STOP) this path is sufficient.
        handleCommand(tag, commandType, correlationId, null, null);
    }

    /**
     * Override point — AbstractMockDevice's MQTT callback calls this with
     * the full parsed command map so we can extract session_id and vss_url.
     * We re-route through here by overriding the MQTT message handler in
     * connectMqtt — but since connectMqtt is private in the base class,
     * we use a different approach: store extra fields in atomic refs before
     * delegating to onCommandReceived.
     *
     * <p>This is called directly from our custom MQTT subscription set up
     * in the first onSteadyStateTick after MQTT connects.
     */
    public void onFullCommandReceived(String tag, Map cmd) {
        String commandType   = (String) cmd.get("command_type");
        String correlationId = (String) cmd.get("correlation_id");
        String sessionId     = (String) cmd.get("session_id");
        String vssUrl        = (String) cmd.get("vss_url");
        handleCommand(tag, commandType, correlationId, sessionId, vssUrl);
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
                    chunkSequence.set(0);

                    System.out.println(tag + " Camera capture started — streaming"
                            + " sessionId=" + sessionId + " vssUrl=" + vssUrl);

                    publishEvent("stream", Map.of(
                            "device_id",      getDeviceId(),
                            "event_type",     "STREAM_STARTED",
                            "occurred_at_ms", System.currentTimeMillis(),
                            "nonce",          nextNonce()
                    ));
                    publishAck(correlationId, commandType, true);

                    // Start sending chunks to VSS if we have a session
                    if (sessionId != null && vssUrl != null) {
                        startChunkSender(tag, sessionId, vssUrl);
                    }
                } else {
                    System.out.println(tag + " Already streaming, acking duplicate");
                    publishAck(correlationId, commandType, true);
                }
            }

            case "STREAM_STOP" -> {
                if (streaming.compareAndSet(true, false)) {
                    System.out.println(tag + " Camera capture stopped");
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

    /**
     * Sends simulated video chunks to the VSS recording session.
     * Runs in a daemon thread until streaming stops or the session ends.
     * Each chunk is a small byte array representing a video frame.
     */
    private void startChunkSender(String tag, String sessionId, String vssUrl) {
        Thread sender = new Thread(() -> {
            ServiceClient chunkClient = new ServiceClient();
            System.out.println(tag + " Chunk sender started: sessionId=" + sessionId);
            long seq = 0;
            while (streaming.get()) {
                try {
                    // Simulate a video chunk — in production this would be
                    // H.264/H.265 encoded frame data from the camera sensor
                    byte[] frameData = ("FRAME_" + getDeviceId() + "_"
                            + seq + "_" + System.currentTimeMillis()).getBytes(
                            StandardCharsets.UTF_8);
                    String b64 = java.util.Base64.getEncoder()
                            .encodeToString(frameData);

                    ServiceClient.ServiceResponse resp = chunkClient.post(
                            vssUrl + "/vss/chunks/ingest",
                            Map.of("recordingSessionId", sessionId,
                                    "sequenceNumber",     seq,
                                    "chunkBytes",         b64));

                    if (resp.isSuccess()) {
                        System.out.println(tag + " Chunk sent: seq=" + seq
                                + " bytes=" + frameData.length);
                    } else {
                        System.err.println(tag + " Chunk rejected: seq=" + seq
                                + " HTTP=" + resp.statusCode());
                    }
                    seq++;
                    Thread.sleep(CHUNK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println(tag + " Chunk send error: " + e.getMessage());
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                }
            }
            System.out.println(tag + " Chunk sender stopped after " + seq + " chunks");
        }, "chunk-sender-" + getDeviceId());
        sender.setDaemon(true);
        sender.start();
    }
}