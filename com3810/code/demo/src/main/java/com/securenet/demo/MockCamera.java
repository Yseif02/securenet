package com.securenet.demo;

import com.securenet.model.DeviceType;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock camera device.
 *
 * <p>Combines motion detection (like {@link MockSensor}) with video
 * streaming capability. Publishes periodic {@code MOTION_DETECTED}
 * events and responds to {@code STREAM_START} / {@code STREAM_STOP}
 * commands.
 *
 * <p>When streaming, the camera simulates pushing video chunks by
 * publishing {@code STREAM_STARTED} and {@code STREAM_ENDED} events.
 * Actual video byte streaming to the Video Streaming Service would
 * happen here in a production implementation.
 */
public class MockCamera extends AbstractMockDevice {

    /** How often to fire a simulated motion event (every N heartbeats). */
    private static final int MOTION_EVENT_INTERVAL = 3;

    private final AtomicBoolean streaming = new AtomicBoolean(false);

    public MockCamera(String deviceId, String registrationToken, String idfsBaseUrl) {
        super(deviceId, registrationToken, DeviceType.CAMERA, idfsBaseUrl);
    }

    /** @return {@code true} if the camera is currently streaming */
    public boolean isStreaming() {
        return streaming.get();
    }

    @Override
    protected void onSteadyStateTick(String tag, int heartbeatCount) {
        // Publish motion events periodically (camera has a built-in motion sensor)
        if (heartbeatCount > 0 && heartbeatCount % MOTION_EVENT_INTERVAL == 0) {
            long nonce = nextNonce();
            long occurredAtMs = System.currentTimeMillis();

            publishEvent("motion", Map.of(
                    "device_id", getDeviceId(),
                    "event_type", "MOTION_DETECTED",
                    "motion_detected", true,
                    "occurred_at_ms", occurredAtMs,
                    "nonce", nonce
            ));

            System.out.println(tag + " Published MOTION_DETECTED nonce=" + nonce);
        }

        // If streaming, simulate chunk delivery log
        if (streaming.get() && heartbeatCount % 2 == 0) {
            System.out.println(tag + " Streaming video chunks...");
        }
    }

    @Override
    protected void onCommandReceived(String tag, String commandType, String correlationId) {
        if (commandType == null) {
            publishAck(correlationId, "UNKNOWN", false);
            return;
        }

        switch (commandType) {
            case "STREAM_START" -> {
                if (streaming.compareAndSet(false, true)) {
                    System.out.println(tag + " Camera capture started — streaming");

                    publishEvent("stream", Map.of(
                            "device_id", getDeviceId(),
                            "event_type", "STREAM_STARTED",
                            "occurred_at_ms", System.currentTimeMillis(),
                            "nonce", nextNonce()
                    ));
                    publishAck(correlationId, commandType, true);
                } else {
                    System.out.println(tag + " Already streaming, acking duplicate");
                    publishAck(correlationId, commandType, true);
                }
            }

            case "STREAM_STOP" -> {
                if (streaming.compareAndSet(true, false)) {
                    System.out.println(tag + " Camera capture stopped");

                    publishEvent("stream", Map.of(
                            "device_id", getDeviceId(),
                            "event_type", "STREAM_ENDED",
                            "occurred_at_ms", System.currentTimeMillis(),
                            "nonce", nextNonce()
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
}
