package com.securenet.model;

/**
 * Lifecycle states a SecureNet IoT device can be in.
 *
 * <p>State transitions follow this general flow:
 * <pre>
 *   PENDING_REGISTRATION → ONLINE → OFFLINE → ONLINE  (normal operation)
 *                                 → UNRESPONSIVE → ONLINE | DEREGISTERED
 *   PENDING_REGISTRATION → DEREGISTERED               (registration failure)
 * </pre>
 */
public enum DeviceStatus {

    /**
     * Device has been manufactured but not yet registered with SecureNet.
     */
    PENDING_REGISTRATION,

    /**
     * Device is connected, sending heartbeats, and accepting commands.
     */
    ONLINE,

    /**
     * Device has lost WiFi connectivity and is operating locally.
     * Buffered data will be synced when connectivity is restored.
     */
    OFFLINE,

    /**
     * Device has not sent a heartbeat within the configured threshold period.
     * The cloud has flagged it for investigation.
     */
    UNRESPONSIVE,

    /**
     * Device has been permanently removed from the user's account.
     */
    DEREGISTERED
}
