package com.securenet.model.bootstrap;

import com.securenet.model.DeviceCredentials;

import java.util.Objects;

/**
 * Immutable value object representing the full provisioning response returned
 * to a device after registration.
 *
 * <p>This may include:
 * <ul>
 *     <li>Basic registration info</li>
 *     <li>Firmware assignment</li>
 *     <li>MQTT credentials (optional, may be null if deferred)</li>
 * </ul>
 *
 * <p>This allows flexible onboarding flows without breaking the API contract.
 */
public record DeviceProvisioningBundle(
        DeviceRegistrationInfo registrationInfo,
        FirmwareAssignment firmwareAssignment,
        DeviceCredentials mqttCredentials
) {

    /**
     * @param registrationInfo basic registration details (required)
     * @param firmwareAssignment firmware to install (required)
     * @param mqttCredentials MQTT credentials (may be null if issued later)
     */
    public DeviceProvisioningBundle (
            DeviceRegistrationInfo registrationInfo,
            FirmwareAssignment firmwareAssignment,
            DeviceCredentials mqttCredentials
    ) {
        this.registrationInfo = Objects.requireNonNull(registrationInfo, "registrationInfo");
        this.firmwareAssignment = Objects.requireNonNull(firmwareAssignment, "firmwareAssignment");
        this.mqttCredentials = mqttCredentials; // optional by design
    }

    public DeviceRegistrationInfo registrationInfo() {
        return registrationInfo;
    }

    public FirmwareAssignment firmwareAssignment() {
        return firmwareAssignment;
    }

    public DeviceCredentials mqttCredentials() {
        return mqttCredentials;
    }
}