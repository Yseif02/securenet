package com.securenet.eventprocessing.impl;

import com.securenet.common.ServiceClient;
import com.securenet.eventprocessing.EventProcessingService;
import com.securenet.eventprocessing.raft.LogEntry;
import com.securenet.eventprocessing.raft.RaftNode;
import com.securenet.model.*;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.storage.StorageGateway;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Raft-replicated implementation of the Event Processing Service.
 *
 * <p>When running in a cluster, this service integrates with
 * {@link RaftNode} for consensus. Event ingestion (writes) go through
 * the Raft leader, which replicates to followers before committing.
 * Event history queries (reads) can be served by any node from the
 * Storage Service.
 *
 * <h3>Distributed Systems Problems Addressed</h3>
 * <ul>
 *   <li><strong>Lamport Clocks (#7):</strong> Assigns L = max(L, deviceTimestamp) + 1</li>
 *   <li><strong>Idempotency (#8):</strong> Dedup table keyed on deviceId:eventType:nonce</li>
 *   <li><strong>Raft Log Replication (#2):</strong> Writes go through Raft consensus</li>
 *   <li><strong>Leader Election (#1):</strong> Via RaftNode</li>
 *   <li><strong>Quorums (#5):</strong> Entries committed only after majority ack</li>
 * </ul>
 */
public class EventProcessingServiceImpl implements EventProcessingService {

    private final AtomicLong lamportClock = new AtomicLong(0);
    private final ConcurrentHashMap<String, DeduplicationEntry> deduplicationTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastMotionAlertByDevice = new ConcurrentHashMap<>();

    private static final long DEDUP_TTL_MS = 24 * 60 * 60 * 1000;
    private static final long MOTION_COOLDOWN_MS = 10_000;

    private final StorageGateway storageGateway;
    private final ServiceClient httpClient;
    private final String notificationServiceUrl;

    /** Raft node — null when running in single-node mode. */
    private volatile RaftNode raftNode;

    /**
     * @param storageGateway         HTTP client to the Storage Service
     * @param notificationServiceUrl base URL of Notification Service (nullable)
     */
    public EventProcessingServiceImpl(StorageGateway storageGateway,
                                      String notificationServiceUrl) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.notificationServiceUrl = notificationServiceUrl;
        this.httpClient = new ServiceClient();
    }

    /**
     * Attaches a Raft node to this EPS instance, enabling replicated
     * consensus for event ingestion.
     */
    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    // =====================================================================
    // Event ingestion — write path
    // =====================================================================

    @Override
    public SecurityEvent ingestEvent(String deviceId, EventType type, Instant occurredAt,
                                     Map<String, String> metadata)
            throws DeviceNotFoundException, IllegalArgumentException {

        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (metadata == null) metadata = Map.of();
        requireNonBlank(deviceId, "deviceId");

        // If Raft is active and we're not the leader, reject the write.
        // The caller (IDFS or API Gateway) should redirect to the leader.
        if (raftNode != null && !raftNode.isLeader()) {
            String leaderId = raftNode.getLeaderId();
            throw new IllegalStateException(
                    "Not the Raft leader. Current leader: " +
                            (leaderId != null ? leaderId : "unknown (election in progress)"));
        }

        // Idempotency check
        String nonce = metadata.getOrDefault("nonce", "");
        String dedupKey = deviceId + ":" + type.name() + ":" + nonce;

        DeduplicationEntry existing = deduplicationTable.get(dedupKey);
        if (existing != null && !existing.isExpired()) {
            System.out.println("[EPS] Duplicate event suppressed: " + dedupKey);
            SecurityEvent original = storageGateway.findEventById(existing.eventId()).orElse(null);
            if (original != null) return original;
        }

        if (raftNode != null) {
            // Replicated path — go through Raft consensus
            return ingestViaRaft(deviceId, type, occurredAt, metadata, nonce, dedupKey);
        } else {
            // Single-node path — apply directly
            return applyEvent(deviceId, type, occurredAt, metadata, dedupKey);
        }
    }

    /**
     * Appends the event to the Raft log and waits for it to be
     * committed (replicated to a quorum). The commit callback
     * then applies the event to the state machine.
     */
    private SecurityEvent ingestViaRaft(String deviceId, EventType type, Instant occurredAt,
                                        Map<String, String> metadata, String nonce,
                                        String dedupKey)
            throws DeviceNotFoundException {

        CompletableFuture<LogEntry> future = raftNode.appendEntry(
                deviceId, type.name(), occurredAt.toString(), nonce, metadata
        );

        if (future == null) {
            throw new IllegalStateException("Lost leadership during append");
        }

        try {
            LogEntry committed = future.get(5, TimeUnit.SECONDS);
            // The commit callback (onRaftCommit) has already applied the event.
            // Look it up by dedup key to return it.
            DeduplicationEntry dedupEntry = deduplicationTable.get(dedupKey);
            if (dedupEntry != null) {
                return storageGateway.findEventById(dedupEntry.eventId()).orElse(null);
            }
            return null;
        } catch (TimeoutException e) {
            throw new RuntimeException("Raft commit timed out — entry may still be replicated", e);
        } catch (Exception e) {
            throw new RuntimeException("Raft commit failed: " + e.getMessage(), e);
        }
    }

    /**
     * Raft commit callback — called when a log entry is committed
     * (replicated to a quorum). Applies the entry to the EPS state
     * machine: assigns Lamport number, enriches, persists, routes alert.
     *
     * <p>This is called on all nodes (leader and followers) so that
     * every node's state machine stays in sync.
     */
    public void onRaftCommit(LogEntry entry) {
        try {
            String deviceId = entry.deviceId();
            EventType type = EventType.valueOf(entry.eventType());
            Instant occurredAt = Instant.parse(entry.occurredAt());
            Map<String, String> metadata = entry.metadata() != null
                    ? new HashMap<>(entry.metadata()) : new HashMap<>();

            String dedupKey = deviceId + ":" + type.name() + ":" + entry.nonce();

            // Dedup check (follower might see replayed entries)
            if (deduplicationTable.containsKey(dedupKey)) return;

            applyEvent(deviceId, type, occurredAt, metadata, dedupKey);
        } catch (Exception e) {
            System.err.println("[EPS] Error applying committed entry: " + e.getMessage());
        }
    }

    /**
     * Core state machine application — shared by single-node and Raft paths.
     */
    private SecurityEvent applyEvent(String deviceId, EventType type, Instant occurredAt,
                                     Map<String, String> metadata, String dedupKey) {
        // Lamport clock
        long deviceTimestampMs = occurredAt.toEpochMilli();
        long sequenceNumber = lamportClock.updateAndGet(current ->
                Math.max(current, deviceTimestampMs) + 1);

        // Enrich
        Device device;
        try {
            device = storageGateway.findDeviceById(deviceId).orElse(null);
        } catch (Exception e) {
            device = null;
        }

        String ownerId = (device != null) ? device.ownerId() : "unknown";
        String displayName = (device != null) ? device.displayName() : deviceId;

        Map<String, String> enrichedMetadata = new HashMap<>(metadata);
        enrichedMetadata.put("lamport_sequence", String.valueOf(sequenceNumber));
        enrichedMetadata.put("device_display_name", displayName);

        // Persist
        String eventId = UUID.randomUUID().toString();
        SecurityEvent event = new SecurityEvent(
                eventId, deviceId, ownerId, type, occurredAt, enrichedMetadata);

        storageGateway.saveEvent(event);
        deduplicationTable.put(dedupKey, new DeduplicationEntry(eventId, System.currentTimeMillis()));

        System.out.println("[EPS] Event committed: id=" + eventId +
                " device=" + deviceId + " type=" + type + " lamport=" + sequenceNumber);

        // Route alert
        routeAlertIfRequired(event);

        return event;
    }

    // =====================================================================
    // Event history queries — read path (any node can serve)
    // =====================================================================

    @Override
    public List<SecurityEvent> getEventHistory(String deviceId, Instant from, Instant to,
                                               int maxEvents)
            throws DeviceNotFoundException, IllegalArgumentException {
        requireNonBlank(deviceId, "deviceId");
        validateTimeRange(from, to);
        validateMaxEvents(maxEvents);
        storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        return storageGateway.findEventsByDevice(deviceId, from, to, maxEvents);
    }

    @Override
    public List<SecurityEvent> getEventsByTypeForOwner(String ownerId, EventType type,
                                                       Instant from, Instant to, int maxEvents) {
        requireNonBlank(ownerId, "ownerId");
        Objects.requireNonNull(type, "type");
        validateTimeRange(from, to);
        validateMaxEvents(maxEvents);
        return storageGateway.findEventsByOwnerAndType(ownerId, type.name(), from, to, maxEvents);
    }

    @Override
    public SecurityEvent getEventById(String eventId) {
        if (eventId == null || eventId.isBlank()) return null;
        return storageGateway.findEventById(eventId).orElse(null);
    }

    // =====================================================================
    // Alert routing
    // =====================================================================

    @Override
    public void routeAlertIfRequired(SecurityEvent event) {
        boolean shouldAlert = switch (event.type()) {
            case MOTION_DETECTED -> {
                long now = System.currentTimeMillis();
                Long lastAlert = lastMotionAlertByDevice.get(event.deviceId());
                if (lastAlert != null && (now - lastAlert) < MOTION_COOLDOWN_MS) {
                    System.out.println("[EPS] Motion alert suppressed (cooldown): " + event.deviceId());
                    yield false;
                }
                lastMotionAlertByDevice.put(event.deviceId(), now);
                yield true;
            }
            case DEVICE_UNRESPONSIVE -> true;
            case DOOR_UNLOCKED -> true;
            default -> false;
        };

        if (!shouldAlert) return;

        System.out.println("[EPS] Alert triggered: type=" + event.type() +
                " device=" + event.deviceId() + " owner=" + event.ownerId());

        if (notificationServiceUrl != null) {
            try {
                httpClient.post(notificationServiceUrl + "/notify/alert",
                        Map.of("eventId", event.eventId(),
                                "deviceId", event.deviceId(),
                                "ownerId", event.ownerId(),
                                "eventType", event.type().name(),
                                "occurredAt", event.occurredAt().toString()));
            } catch (Exception e) {
                System.err.println("[EPS] Failed to forward alert: " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // Accessors
    // =====================================================================

    public long getLamportClock() { return lamportClock.get(); }
    public void setLamportClock(long value) { lamportClock.set(value); }
    public int getDeduplicationTableSize() { return deduplicationTable.size(); }
    public RaftNode getRaftNode() { return raftNode; }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(fieldName + " is required");
    }

    private static void validateTimeRange(Instant from, Instant to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from))
            throw new IllegalArgumentException("'to' must not be before 'from'");
    }

    private static void validateMaxEvents(int maxEvents) {
        if (maxEvents < 1 || maxEvents > 500)
            throw new IllegalArgumentException("maxEvents must be between 1 and 500");
    }

    private record DeduplicationEntry(String eventId, long recordedAtMs) {
        boolean isExpired() {
            return (System.currentTimeMillis() - recordedAtMs) > DEDUP_TTL_MS;
        }
    }
}