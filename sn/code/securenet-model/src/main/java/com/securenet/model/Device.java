package com.securenet.model;

//import com.securenet.storage.StorageService;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing a registered SecureNet IoT device.
 *
 * <p>A {@code Device} is created during the onboarding workflow and stored in
 * the { com.securenet.storage.StorageService}. All fields are set at
 * construction time; use {@link #withStatus(DeviceStatus)} to derive an updated
 * copy when the device lifecycle changes.
 *
 * <p>Example:</p>
 * <pre>{@code
 * Device cam = new Device(
 *     "dev-001", "Living Room Camera", DeviceType.CAMERA,
 *     "user-42", DeviceStatus.ONLINE, Instant.now(), "1.4.2");
 * Device offline = cam.withStatus(DeviceStatus.OFFLINE);
 * }</pre>
 */
public record Device(String deviceId, String displayName, DeviceType type, String ownerId, DeviceStatus status,
                     Instant registeredAt, String firmwareVersion) {

    /**
     * Constructs a fully-initialised Device.
     *
     * @param deviceId        unique identifier assigned during registration
     * @param displayName     human-readable label chosen by the homeowner
     * @param type            hardware category of the device
     * @param ownerId         identifier of the {@link User} who owns this device
     * @param status          current lifecycle status
     * @param registeredAt    UTC instant at which the device was registered
     * @param firmwareVersion semantic version string of the installed firmware
     */
    public Device(String deviceId, String displayName, DeviceType type, String ownerId, DeviceStatus status, Instant registeredAt, String firmwareVersion) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.type = Objects.requireNonNull(type, "type");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.status = Objects.requireNonNull(status, "status");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.firmwareVersion = Objects.requireNonNull(firmwareVersion, "firmwareVersion");
    }

    /**
     * @return platform-assigned unique device identifier
     */
    @Override
    public String deviceId() {
        return deviceId;
    }

    /**
     * @return homeowner-visible label for this device
     */
    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * @return DeviceType
     */
    @Override
    public DeviceType type() {
        return type;
    }

    /**
     * @return identifier of the owning {@link User}
     */
    @Override
    public String ownerId() {
        return ownerId;
    }

    /**
     * @return current lifecycle status
     */
    @Override
    public DeviceStatus status() {
        return status;
    }

    /**
     * @return UTC timestamp of initial registration
     */
    @Override
    public Instant registeredAt() {
        return registeredAt;
    }

    /**
     * @return semantic version of the firmware currently running on this device
     */
    @Override
    public String firmwareVersion() {
        return firmwareVersion;
    }

    /**
     * Returns a new {@code Device} identical to this one but with the given status.
     *
     * @param newStatus the status to apply
     * @return a new immutable Device with the updated status
     */
    public Device withStatus(DeviceStatus newStatus) {
        return new Device(deviceId, displayName, type, ownerId, newStatus, registeredAt, firmwareVersion);
    }

    /**
     * Returns a new {@code Device} with an updated firmware version.
     *
     * @param newVersion the firmware version just installed
     * @return a new immutable Device reflecting the upgrade
     */
    public Device withFirmwareVersion(String newVersion) {
        return new Device(deviceId, displayName, type, ownerId, status, registeredAt, newVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Device d)) return false;
        return deviceId.equals(d.deviceId);
    }

    @Override
    public int hashCode() {
        return deviceId.hashCode();
    }

    @Override
    public String toString() {
        return "Device{id=" + deviceId + ", name=" + displayName +
                ", type=" + type + ", status=" + status + "}";
    }
}
