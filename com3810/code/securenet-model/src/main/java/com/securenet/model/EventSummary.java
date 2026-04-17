package com.securenet.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight summary of a security event for display in the client
 * application's event timeline.
 *
 * <p>Contains only the fields needed for UI rendering — the full
 * {@link SecurityEvent} with all metadata can be fetched separately
 * if the homeowner taps on an event for details.
 */
public record EventSummary(
        String eventId,
        String deviceId,
        String deviceDisplayName,
        EventType type,
        Instant occurredAt,
        boolean hasFootage
) {
    public EventSummary {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /**
     * Creates an EventSummary from a full SecurityEvent.
     */
    public static EventSummary from(SecurityEvent event) {
        String displayName = event.metadata().getOrDefault(
                "device_display_name", event.deviceId());
        boolean hasFootage = event.metadata().containsKey("clipId");

        return new EventSummary(
                event.eventId(),
                event.deviceId(),
                displayName,
                event.type(),
                event.occurredAt(),
                hasFootage
        );
    }
}
