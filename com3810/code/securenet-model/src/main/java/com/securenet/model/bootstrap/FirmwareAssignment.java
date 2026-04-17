package com.securenet.model.bootstrap;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object describing firmware assigned to a device during
 * the onboarding or update process.
 *
 * <p>This object is returned after registration and is used by the device
 * bootloader to download and install the correct firmware image.
 *
 * <p>The firmware URL should point to a secure, authenticated endpoint.
 */
public record FirmwareAssignment(
        String deviceId,
        String firmwareVersion,
        String firmwareUrl,
        Instant issuedAt
) {

    /**
     * @param deviceId         device receiving the firmware
     * @param firmwareVersion  semantic version of the firmware (e.g. 1.0.0)
     * @param firmwareUrl      downloadable location of the firmware binary
     * @param issuedAt         UTC instant assignment was created
     */
    public FirmwareAssignment (
            String deviceId,
            String firmwareVersion,
            String firmwareUrl,
            Instant issuedAt
    ){
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.firmwareVersion = Objects.requireNonNull(firmwareVersion, "firmwareVersion");
        this.firmwareUrl = Objects.requireNonNull(firmwareUrl, "firmwareUrl");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public String deviceId() {
        return deviceId;
    }

    public String firmwareVersion() {
        return firmwareVersion;
    }

    public String firmwareUrl() {
        return firmwareUrl;
    }

    public Instant issuedAt() {
        return issuedAt;
    }
}