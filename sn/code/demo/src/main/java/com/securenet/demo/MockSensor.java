package com.securenet.demo;

import com.securenet.model.DeviceType;

import java.util.Map;

/**
 * Mock motion sensor device.
 *
 * <p>Publishes a {@code MOTION_DETECTED} event every 3rd heartbeat cycle
 * (roughly every 90 seconds) on topic
 * {@code securenet/devices/{deviceId}/events/motion}.
 *
 * <p>Responds to incoming commands with a success ack but performs no
 * physical actuation (motion sensors are passive devices).
 */
public class MockSensor extends AbstractMockDevice {

    /** How often to fire a simulated motion event (every N heartbeats). */
    private static final int MOTION_EVENT_INTERVAL = 3;

    public MockSensor(String deviceId, String registrationToken, String idfsBaseUrl) {
        super(deviceId, registrationToken, DeviceType.MOTION_SENSOR, idfsBaseUrl);
    }

    @Override
    protected void onSteadyStateTick(String tag, int heartbeatCount) {
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
    }

    @Override
    protected void onCommandReceived(String tag, String commandType, String correlationId) {
        // Motion sensors are passive — ack but no actuation
        System.out.println(tag + " Received command (passive device, acking): " + commandType);
        publishAck(correlationId, commandType, true);
    }
}
