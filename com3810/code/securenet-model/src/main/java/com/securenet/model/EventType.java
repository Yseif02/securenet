package com.securenet.model;

/**
 * Types of security events that IoT devices can emit to the SecureNet platform.
 *
 * <p>The {@link com.securenet.eventprocessing.EventProcessingService} uses this
 * enumeration to decide enrichment logic and whether to trigger a push alert.
 */
public enum EventType {
    /** Motion detected by a camera or dedicated sensor. */
    MOTION_DETECTED,
    /** Smart lock successfully engaged (locked). */
    DOOR_LOCKED,
    /** Smart lock successfully disengaged (unlocked). */
    DOOR_UNLOCKED,
    /** Camera video stream started. */
    STREAM_STARTED,
    /** Camera video stream ended. */
    STREAM_ENDED,
    /** Device lost WiFi and switched to local-only operation. */
    DEVICE_WENT_OFFLINE,
    /** Device restored WiFi and re-connected to the cloud. */
    DEVICE_CAME_ONLINE,
    /** Device has not sent a heartbeat within the threshold window. */
    DEVICE_UNRESPONSIVE,
    /** Firmware update was applied to a device. */
    FIRMWARE_UPDATED
}
