package com.securenet.videostreaming.impl;

import com.securenet.model.VideoClip;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.VideoNotFoundException;
import com.securenet.storage.StorageGateway;
import com.securenet.videostreaming.VideoStreamingService;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the Video Streaming Service.
 *
 * <p>Recording session metadata (sessionId, deviceId, ownerId, startedAt)
 * is persisted in PostgreSQL so that any VSS instance behind the load
 * balancer can look up active sessions. Chunk data is buffered in-memory
 * during the session (transient by nature — chunks are assembled and
 * archived to PostgreSQL when the session closes).
 *
 * <p>The {@code recording_sessions} table in PostgreSQL replaces the
 * old in-memory {@code activeSessions} and {@code deviceToSession} maps.
 */
public class VideoStreamingServiceImpl implements VideoStreamingService {

    private final StorageGateway storageGateway;

    /**
     * In-memory chunk buffer keyed by sessionId.
     * <p>Chunks are inherently transient — they exist only during an
     * active recording session and are assembled into a VideoClip when
     * the session closes. If the VSS instance crashes mid-session, the
     * chunks are lost (same as real camera streams). Session metadata
     * is in PostgreSQL so a new VSS instance can detect stale sessions.
     */
    private final ConcurrentHashMap<String, TreeMap<Long, byte[]>> sessionChunks =
            new ConcurrentHashMap<>();

    private static final String SIGNING_SECRET = "securenet-vss-signing-key";

    public VideoStreamingServiceImpl(StorageGateway storageGateway) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
    }

    // =====================================================================
    // Recording session lifecycle
    // =====================================================================

    @Override
    public String openRecordingSession(String deviceId, String ownerId)
            throws DeviceNotFoundException, IllegalStateException {
        requireNonBlank(deviceId, "deviceId");
        requireNonBlank(ownerId, "ownerId");

        storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        // Check for existing active session — from PostgreSQL
        Optional<String> existingSession = storageGateway.findActiveSessionForDevice(deviceId);
        if (existingSession.isPresent()) {
            throw new IllegalStateException(
                    "Recording session already active for device: " + deviceId);
        }

        String sessionId = "rec-" + UUID.randomUUID();
        Instant now = Instant.now();

        // Persist session metadata to PostgreSQL
        storageGateway.saveRecordingSession(sessionId, deviceId, ownerId, now);

        // Initialize in-memory chunk buffer
        sessionChunks.put(sessionId, new TreeMap<>());

        System.out.println("[VSS] Opened recording session: " + sessionId +
                " device=" + deviceId);
        return sessionId;
    }

    @Override
    public void closeRecordingSession(String recordingSessionId)
            throws IllegalArgumentException {
        requireNonBlank(recordingSessionId, "recordingSessionId");

        // Look up session metadata from PostgreSQL
        Map<String, String> session = storageGateway.findRecordingSession(recordingSessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or already closed session: " + recordingSessionId));

        String deviceId = session.get("deviceId");
        String ownerId = session.get("ownerId");
        Instant startedAt = Instant.parse(session.get("startedAt"));

        // Remove from PostgreSQL
        storageGateway.deleteRecordingSession(recordingSessionId);

        // Assemble chunks from in-memory buffer
        TreeMap<Long, byte[]> chunks = sessionChunks.remove(recordingSessionId);
        byte[] assembledBytes;
        if (chunks != null && !chunks.isEmpty()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] chunk : chunks.values()) {
                out.writeBytes(chunk);
            }
            assembledBytes = out.toByteArray();
        } else {
            assembledBytes = new byte[0];
        }

        if (assembledBytes.length == 0) {
            System.out.println("[VSS] Session " + recordingSessionId +
                    " closed with no chunks, skipping archive");
            return;
        }

        Duration duration = Duration.between(startedAt, Instant.now());

        try {
            VideoClip clip = archiveFootage(deviceId, ownerId, startedAt, assembledBytes);
            System.out.println("[VSS] Session " + recordingSessionId +
                    " archived as clip " + clip.clipId() +
                    " (" + assembledBytes.length + " bytes, " +
                    duration.toSeconds() + "s)");
        } catch (Exception e) {
            System.err.println("[VSS] Failed to archive session " +
                    recordingSessionId + ": " + e.getMessage());
        }
    }

    // =====================================================================
    // Chunk ingestion
    // =====================================================================

    @Override
    public void ingestChunk(String recordingSessionId, long sequenceNumber,
                            byte[] chunkBytes) throws IllegalArgumentException {
        requireNonBlank(recordingSessionId, "recordingSessionId");
        Objects.requireNonNull(chunkBytes, "chunkBytes");

        // Verify session exists in PostgreSQL
        storageGateway.findRecordingSession(recordingSessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or closed session: " + recordingSessionId));

        // Buffer chunk in memory
        sessionChunks.computeIfAbsent(recordingSessionId, k -> new TreeMap<>())
                .put(sequenceNumber, chunkBytes);
    }

    // =====================================================================
    // Footage archiving
    // =====================================================================

    @Override
    public VideoClip archiveFootage(String deviceId, String ownerId,
                                    Instant segmentStarted, byte[] rawBytes)
            throws VideoNotFoundException, DeviceNotFoundException {
        requireNonBlank(deviceId, "deviceId");
        requireNonBlank(ownerId, "ownerId");

        storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        String clipId = "clip-" + UUID.randomUUID();
        String storageKey = "video/" + deviceId + "/" + clipId;
        Duration duration = Duration.between(segmentStarted, Instant.now());

        VideoClip clip = new VideoClip(
                clipId, deviceId, ownerId, segmentStarted,
                duration, rawBytes.length, storageKey);

        storageGateway.saveVideoClip(clip, rawBytes);
        return clip;
    }

    // =====================================================================
    // Playback
    // =====================================================================

    @Override
    public List<VideoClip> findClips(String deviceId, Instant from, Instant to)
            throws DeviceNotFoundException, IllegalArgumentException {
        requireNonBlank(deviceId, "deviceId");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }

        storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        return storageGateway.findClipsByDevice(deviceId, from, to);
    }

    @Override
    public String generateSignedPlaybackUrl(String clipId, int validForSeconds)
            throws VideoNotFoundException, IllegalArgumentException {
        requireNonBlank(clipId, "clipId");
        if (validForSeconds < 1 || validForSeconds > 3600) {
            throw new IllegalArgumentException(
                    "validForSeconds must be between 1 and 3600");
        }

        VideoClip clip = storageGateway.findClipById(clipId)
                .orElseThrow(() -> new VideoNotFoundException(clipId));

        long expiresAt = System.currentTimeMillis() + (validForSeconds * 1000L);
        String signature = generateSignature(clip.storageKey(), expiresAt);

        return "/playback/" + clipId +
                "?expires=" + expiresAt +
                "&sig=" + signature;
    }

    private static String generateSignature(String storageKey, long expiresAt) {
        String data = storageKey + ":" + expiresAt + ":" + SIGNING_SECRET;
        return Integer.toHexString(data.hashCode());
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(fieldName + " is required");
    }
}