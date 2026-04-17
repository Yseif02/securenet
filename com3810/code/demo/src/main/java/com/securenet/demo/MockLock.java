package com.securenet.demo;

import com.securenet.model.DeviceType;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock smart lock device.
 *
 * <p>Maintains an internal locked/unlocked state. Responds to
 * {@code LOCK} and {@code UNLOCK} MQTT commands by toggling state,
 * publishing a {@code DOOR_LOCKED} or {@code DOOR_UNLOCKED} event,
 * and sending an ack back on the device's ack topic.
 *
 * <p>Does not publish periodic events — the lock is a command-driven
 * device that only emits events in response to commands.
 */
public class MockLock extends AbstractMockDevice {

    private final AtomicBoolean locked = new AtomicBoolean(false);

    public MockLock(String deviceId, String registrationToken, String idfsBaseUrl) {
        super(deviceId, registrationToken, DeviceType.SMART_LOCK, idfsBaseUrl);
    }

    /** @return {@code true} if the lock is currently in the locked state */
    public boolean isLocked() {
        return locked.get();
    }

    @Override
    protected void onSteadyStateTick(String tag, int heartbeatCount) {
        // Smart locks are passive between commands — no periodic events
    }

    @Override
    protected void onCommandReceived(String tag, String commandType, String correlationId) {
        if (commandType == null) {
            System.out.println(tag + " Received null command type, ignoring");
            publishAck(correlationId, "UNKNOWN", false);
            return;
        }

        switch (commandType) {
            case "LOCK" -> {
                locked.set(true);
                System.out.println(tag + " Lock motor engaged — door LOCKED");

                publishEvent("lock", Map.of(
                        "device_id", getDeviceId(),
                        "event_type", "DOOR_LOCKED",
                        "lock_state", "LOCKED",
                        "occurred_at_ms", System.currentTimeMillis(),
                        "nonce", nextNonce()
                ));
                publishAck(correlationId, commandType, true);
            }

            case "UNLOCK" -> {
                locked.set(false);
                System.out.println(tag + " Lock motor disengaged — door UNLOCKED");

                publishEvent("lock", Map.of(
                        "device_id", getDeviceId(),
                        "event_type", "DOOR_UNLOCKED",
                        "lock_state", "UNLOCKED",
                        "occurred_at_ms", System.currentTimeMillis(),
                        "nonce", nextNonce()
                ));
                publishAck(correlationId, commandType, true);
            }

            default -> {
                System.out.println(tag + " Unknown command: " + commandType);
                publishAck(correlationId, commandType, false);
            }
        }
    }
}
