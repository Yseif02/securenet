package com.securenet.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing a security event emitted by an IoT device.
 *
 * <p>Events flow from IoT Device Firmware → Event Processing Service →
 * (optionally) Notification Service and Data Storage Layer.
 *
 * <p>The {@code metadata} map carries event-type-specific fields such as
 * {@code "thumbnailUrl"} for {@link EventType#MOTION_DETECTED} or
 * {@code "lockState"} for door lock events.
 */
public record SecurityEvent(String eventId, String deviceId, String ownerId, EventType type, Instant occurredAt,
                            Map<String, String> metadata) {

    /**
     * @param eventId    platform-assigned unique event identifier
     * @param deviceId   identifier of the device that raised this event
     * @param ownerId    identifier of the homeowner associated with this device
     * @param type       category of security event
     * @param occurredAt UTC instant at which the event was detected on the device
     * @param metadata   additional key-value pairs specific to the event type;
     *                   may be empty but never {@code null}
     */
    public SecurityEvent(String eventId, String deviceId, String ownerId, EventType type, Instant occurredAt, Map<String, String> metadata) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.type = Objects.requireNonNull(type, "type");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.metadata = Collections.unmodifiableMap(Objects.requireNonNull(metadata, "metadata"));
    }

    /**
     * @return platform-assigned unique event identifier
     */
    @Override
    public String eventId() {
        return eventId;
    }

    /**
     * @return identifier of the originating device
     */
    @Override
    public String deviceId() {
        return deviceId;
    }

    /**
     * @return identifier of the device owner
     */
    @Override
    public String ownerId() {
        return ownerId;
    }

    /**
     * @return security event category
     */
    @Override
    public EventType type() {
        return type;
    }

    /**
     * @return UTC instant at which the event was detected
     */
    @Override
    public Instant occurredAt() {
        return occurredAt;
    }

    /**
     * @return unmodifiable map of event-type-specific key-value metadata;
     * never {@code null}, may be empty
     */
    @Override
    public Map<String, String> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecurityEvent e)) return false;
        return eventId.equals(e.eventId);
    }

    @Override
    public int hashCode() {
        return eventId.hashCode();
    }

    @Override
    public String toString() {
        return "SecurityEvent{id=" + eventId + ", device=" + deviceId +
                ", type=" + type + ", at=" + occurredAt + "}";
    }
}
