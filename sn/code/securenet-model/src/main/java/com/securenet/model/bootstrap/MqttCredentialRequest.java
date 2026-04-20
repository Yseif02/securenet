package com.securenet.model.bootstrap;

import java.util.Objects;

/**
 * Request object used by a device to obtain MQTT credentials after
 * firmware installation.
 *
 * <p>This call is typically authenticated using device identity and
 * potentially a short-lived provisioning token.
 */
public record MqttCredentialRequest(String deviceId) {

    /**
     * @param deviceId device requesting MQTT credentials
     */
    public MqttCredentialRequest(String deviceId) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
    }

    public String deviceId() {
        return deviceId;
    }
}