package com.securenet.model.bootstrap;

import java.util.Objects;

/**
 * Immutable value object representing the bootstrap provisioning result
 * returned to a device after successful registration.
 *
 * <p>This object deliberately separates bootstrap onboarding from runtime
 * messaging credentials. It tells the bootloader:
 *
 * <ul>
 *   <li>which device identity was accepted, and</li>
 *   <li>which firmware image should be installed next.</li>
 * </ul>
 *
 * <p>MQTT credentials are issued later by a separate runtime provisioning flow
 * once the correct device-specific firmware is installed and running.
 */
public record BootstrapRegistrationResult(
        DeviceRegistrationInfo registrationInfo,
        FirmwareAssignment firmwareAssignment
) {

    /**
     * @param registrationInfo accepted bootstrap registration details
     * @param firmwareAssignment firmware assigned to this device
     */
    public BootstrapRegistrationResult(
            DeviceRegistrationInfo registrationInfo,
            FirmwareAssignment firmwareAssignment
    ) {
        this.registrationInfo = Objects.requireNonNull(registrationInfo, "registrationInfo");
        this.firmwareAssignment = Objects.requireNonNull(firmwareAssignment, "firmwareAssignment");
    }

    /**
     * @return accepted bootstrap registration details
     */
    @Override
    public DeviceRegistrationInfo registrationInfo() {
        return registrationInfo;
    }

    /**
     * @return firmware assignment for the device bootloader
     */
    @Override
    public FirmwareAssignment firmwareAssignment() {
        return firmwareAssignment;
    }
}