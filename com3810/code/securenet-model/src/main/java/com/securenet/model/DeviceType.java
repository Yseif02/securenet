package com.securenet.model;

/**
 * Categories of IoT hardware devices supported by the SecureNet platform.
 */
public enum DeviceType {
    /** IP camera capable of live streaming and local recording. */
    CAMERA,
    /** Electronically actuated door lock controllable via MQTT command. */
    SMART_LOCK,
    /** Passive infrared or microwave motion sensor. */
    MOTION_SENSOR,
    /** General-purpose sensor not covered by the above categories. */
    OTHER
}
