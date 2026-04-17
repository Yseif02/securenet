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

/**
 * Raft-replicated implementation of the Event Processing Service.
 *
 * <p><strong>All state is persisted in PostgreSQL</strong> — no in-memory
 * ConcurrentHashMaps. This ensures any EPS instance behind the load
 * balancer can serve reads, and the Raft leader can be replaced without
 * losing dedup/cooldown/Lamport state.
 *
 * <h3>Distributed Systems Problems Addressed</h3>
 * <ul>
 *   <li><strong>Lamport Clocks (#7):</strong> L = max(L, deviceTimestamp) + 1,
 *       persisted to {@code eps_lamport_clock} table</li>
 *   <li><strong>Idempotency (#8):</strong> Dedup table in {@code eps_dedup} DB table,
 *       keyed on deviceId:eventType:nonce with 24h TTL</li>
 *   <li><strong>Raft Log Replication (#2):</strong> Writes go through Raft consensus</li>
 *   <li><strong>Leader Election (#1):</strong> Via RaftNode</li>
 *   <li><strong>Quorums (#5):</strong> Entries committed after majority ack</li>
 * </ul>
 */
public class EventProcessingServiceImpl implements EventProcessingService {

    /** TTL for deduplication entries — 24 hours. */
    private static final long DEDUP_TTL_MS = 24 * 60 * 60 * 1000;

    /** Motion alert cooldown — 10 seconds. */
    private static final long MOTION_COOLDOWN_MS = 10_000;

    private final StorageGateway storageGateway;
    private final ServiceClient httpClient;
    private final String notificationServiceUrl;
    private final String nodeId;

    /** Raft node — null when running in single-node mode. */
    private volatile RaftNode raftNode;

    /**
     * @param storageGateway         HTTP client to the Storage Service
     * @param notificationServiceUrl base URL of Notification Service (nullable)
     * @param nodeId                 this EPS node's identifier (for Lamport clock key)
     */
    public EventProcessingServiceImpl(StorageGateway storageGateway,
                                      String notificationServiceUrl,
                                      String nodeId) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.notificationServiceUrl = notificationServiceUrl;
        this.nodeId = (nodeId != null) ? nodeId : "global";
        this.httpClient = new ServiceClient();
    }

    /** Backward-compatible 2-arg constructor for single-node mode. */
    public EventProcessingServiceImpl(StorageGateway storageGateway,
                                      String notificationServiceUrl) {
        this(storageGateway, notificationServiceUrl, "global");
    }

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

        // If Raft is active and we're not the leader, reject the write
        if (raftNode != null && !raftNode.isLeader()) {
            String leaderId = raftNode.getLeaderId();
            throw new IllegalStateException(
                    "Not the Raft leader. Current leader: " +
                            (leaderId != null ? leaderId : "unknown (election in progress)"));
        }

        // Idempotency check — from PostgreSQL
        String nonce = metadata.getOrDefault("nonce", "");
        String dedupKey = deviceId + ":" + type.name() + ":" + nonce;

        Optional<Map<String, String>> existingEntry = storageGateway.findDeduplicationEntry(dedupKey);
        if (existingEntry.isPresent()) {
            Instant recordedAt = Instant.parse(existingEntry.get().get("recordedAt"));
            long age = System.currentTimeMillis() - recordedAt.toEpochMilli();
            if (age < DEDUP_TTL_MS) {
                System.out.println("[EPS] Duplicate event suppressed: " + dedupKey);
                String originalEventId = existingEntry.get().get("eventId");
                SecurityEvent original = storageGateway.findEventById(originalEventId).orElse(null);
                if (original != null) return original;
            }
        }

        if (raftNode != null) {
            return ingestViaRaft(deviceId, type, occurredAt, metadata, nonce, dedupKey);
        } else {
            return applyEvent(deviceId, type, occurredAt, metadata, dedupKey);
        }
    }

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
            Optional<Map<String, String>> dedupEntry = storageGateway.findDeduplicationEntry(dedupKey);
            if (dedupEntry.isPresent()) {
                return storageGateway.findEventById(dedupEntry.get().get("eventId")).orElse(null);
            }
            return null;
        } catch (TimeoutException e) {
            throw new RuntimeException("Raft commit timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Raft commit failed: " + e.getMessage(), e);
        }
    }

    /**
     * Raft commit callback — called on all nodes when an entry is committed.
     */
    public void onRaftCommit(LogEntry entry) {
        try {
            String deviceId = entry.deviceId();
            EventType type = EventType.valueOf(entry.eventType());
            Instant occurredAt = Instant.parse(entry.occurredAt());
            Map<String, String> metadata = entry.metadata() != null
                    ? new HashMap<>(entry.metadata()) : new HashMap<>();

            String dedupKey = deviceId + ":" + type.name() + ":" + entry.nonce();

            // Check if already applied (follower replay)
            if (storageGateway.findDeduplicationEntry(dedupKey).isPresent()) return;

            applyEvent(deviceId, type, occurredAt, metadata, dedupKey);
        } catch (Exception e) {
            System.err.println("[EPS] Error applying committed entry: " + e.getMessage());
        }
    }

    /**
     * Core state machine application — shared by single-node and Raft paths.
     * All state mutations go to PostgreSQL.
     */
    private SecurityEvent applyEvent(String deviceId, EventType type, Instant occurredAt,
                                     Map<String, String> metadata, String dedupKey) {
        // Lamport clock — read from DB, increment, write back
        long currentClock = storageGateway.findLamportClock(nodeId);
        long deviceTimestampMs = occurredAt.toEpochMilli();
        long sequenceNumber = Math.max(currentClock, deviceTimestampMs) + 1;
        storageGateway.saveLamportClock(nodeId, sequenceNumber);

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

        // Persist event
        String eventId = UUID.randomUUID().toString();
        SecurityEvent event = new SecurityEvent(
                eventId, deviceId, ownerId, type, occurredAt, enrichedMetadata);

        storageGateway.saveEvent(event);

        // Record in dedup table (PostgreSQL)
        storageGateway.saveDeduplicationEntry(dedupKey, eventId, Instant.now());

        System.out.println("[EPS] Event committed: id=" + eventId +
                " device=" + deviceId + " type=" + type + " lamport=" + sequenceNumber);

        // Route alert
        routeAlertIfRequired(event);

        // Periodic cleanup of expired dedup entries
        if (sequenceNumber % 100 == 0) {
            Instant cutoff = Instant.now().minusMillis(DEDUP_TTL_MS);
            int removed = storageGateway.deleteExpiredDeduplicationEntries(cutoff);
            if (removed > 0) {
                System.out.println("[EPS] Dedup cleanup: removed " + removed + " expired entries");
            }
        }

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
    // Alert routing — motion cooldown in PostgreSQL
    // =====================================================================

    @Override
    public void routeAlertIfRequired(SecurityEvent event) {
        boolean shouldAlert = switch (event.type()) {
            case MOTION_DETECTED -> {
                Instant now = Instant.now();
                // Check cooldown from PostgreSQL
                Optional<Instant> lastAlert = storageGateway.findMotionCooldown(event.deviceId());
                if (lastAlert.isPresent()) {
                    long elapsed = now.toEpochMilli() - lastAlert.get().toEpochMilli();
                    if (elapsed < MOTION_COOLDOWN_MS) {
                        System.out.println("[EPS] Motion alert suppressed (cooldown): " + event.deviceId());
                        yield false;
                    }
                }
                // Update cooldown in PostgreSQL
                storageGateway.saveMotionCooldown(event.deviceId(), now);
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

    public long getLamportClock() { return storageGateway.findLamportClock(nodeId); }
    public void setLamportClock(long value) { storageGateway.saveLamportClock(nodeId, value); }
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
}