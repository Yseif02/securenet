package com.securenet.eventprocessing.raft;

import java.util.Map;
import java.util.Objects;

/**
 * A single entry in the Raft replicated log.
 *
 * <p>Each entry records the leader's term when the entry was appended,
 * a monotonically increasing index, and the event data to be applied
 * to the state machine (the EPS) once committed.
 *
 * <p>The Raft paper requires that entries are uniquely identified by
 * their (term, index) pair. Two entries at the same index with the
 * same term are guaranteed to contain the same data.
 *
 * @param index     1-based position in the log (assigned by the leader)
 * @param term      the leader's term when this entry was created
 * @param deviceId  the device that emitted the event
 * @param eventType the event type (e.g. "MOTION_DETECTED")
 * @param occurredAt ISO-8601 timestamp from the device
 * @param nonce     idempotency nonce from the device
 * @param metadata  event-type-specific key-value pairs
 */
public record LogEntry(
        long index,
        long term,
        String deviceId,
        String eventType,
        String occurredAt,
        String nonce,
        Map<String, String> metadata
) {
    public LogEntry {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (metadata == null) metadata = Map.of();
    }
}
