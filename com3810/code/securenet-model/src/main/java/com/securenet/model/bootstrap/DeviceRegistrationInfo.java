package com.securenet.model.bootstrap;

import com.securenet.model.DeviceType;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing the result of a successful bootstrap
 * registration handshake for a device.
 *
 * <p>This object confirms that the device identity and registration token were
 * accepted by the platform. At this stage the device is known to SecureNet,
 * but it has not yet been provisioned with runtime MQTT credentials.
 *
 * <p>The bootloader uses this information to determine which firmware image it
 * should install next.
 */
public record DeviceRegistrationInfo(
        String deviceId,
        DeviceType deviceType,
        Instant registeredAt
) {

    /**
     * @param deviceId     unique identifier of the device
     * @param deviceType   hardware category of the device
     * @param registeredAt UTC instant the bootstrap registration was accepted
     */
    public DeviceRegistrationInfo(String deviceId, DeviceType deviceType, Instant registeredAt) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
    }

    /**
     * @return unique identifier of the device
     */
    @Override
    public String deviceId() {
        return deviceId;
    }

    /**
     * @return device hardware type
     */
    @Override
    public DeviceType deviceType() {
        return deviceType;
    }

    /**
     * @return UTC instant the bootstrap registration was accepted
     */
    @Override
    public Instant registeredAt() {
        return registeredAt;
    }
}