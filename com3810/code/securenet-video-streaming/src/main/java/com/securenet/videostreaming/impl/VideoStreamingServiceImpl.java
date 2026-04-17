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
 * <p>Manages recording sessions triggered by motion events. Cameras
 * push chunks via IDFS; this service buffers them per session and
 * archives completed footage to the Data Storage Layer when the
 * session closes.
 *
 * <p>No live streaming — all footage is motion-triggered, archived
 * immediately, and available for historical playback via signed URLs.
 */
public class VideoStreamingServiceImpl implements VideoStreamingService {

    private final StorageGateway storageGateway;

    /** Active recording sessions keyed by sessionId. */
    private final ConcurrentHashMap<String, RecordingSession> activeSessions =
            new ConcurrentHashMap<>();

    /** Map from deviceId to active sessionId — one session per device. */
    private final ConcurrentHashMap<String, String> deviceToSession =
            new ConcurrentHashMap<>();

    /** Secret key for signing playback URLs (simplified). */
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

        if (deviceToSession.containsKey(deviceId)) {
            throw new IllegalStateException(
                    "Recording session already active for device: " + deviceId);
        }

        String sessionId = "rec-" + UUID.randomUUID();
        RecordingSession session = new RecordingSession(sessionId, deviceId, ownerId, Instant.now());
        activeSessions.put(sessionId, session);
        deviceToSession.put(deviceId, sessionId);

        System.out.println("[VSS] Opened recording session: " + sessionId +
                " device=" + deviceId);
        return sessionId;
    }

    @Override
    public void closeRecordingSession(String recordingSessionId)
            throws IllegalArgumentException {
        requireNonBlank(recordingSessionId, "recordingSessionId");

        RecordingSession session = activeSessions.remove(recordingSessionId);
        if (session == null) {
            throw new IllegalArgumentException(
                    "Unknown or already closed session: " + recordingSessionId);
        }
        deviceToSession.remove(session.deviceId);

        byte[] assembledBytes = session.assembleChunks();
        if (assembledBytes.length == 0) {
            System.out.println("[VSS] Session " + recordingSessionId +
                    " closed with no chunks, skipping archive");
            return;
        }

        Duration duration = Duration.between(session.startedAt, Instant.now());

        try {
            VideoClip clip = archiveFootage(
                    session.deviceId, session.ownerId,
                    session.startedAt, assembledBytes);
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

        RecordingSession session = activeSessions.get(recordingSessionId);
        if (session == null) {
            throw new IllegalArgumentException(
                    "Unknown or closed session: " + recordingSessionId);
        }

        session.addChunk(sequenceNumber, chunkBytes);
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

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(fieldName + " is required");
    }

    /**
     * Internal recording session state — buffers chunks in sequence order.
     */
    private static class RecordingSession {
        final String sessionId;
        final String deviceId;
        final String ownerId;
        final Instant startedAt;
        final TreeMap<Long, byte[]> chunks = new TreeMap<>();

        RecordingSession(String sessionId, String deviceId, String ownerId, Instant startedAt) {
            this.sessionId = sessionId;
            this.deviceId = deviceId;
            this.ownerId = ownerId;
            this.startedAt = startedAt;
        }

        synchronized void addChunk(long seq, byte[] data) {
            chunks.put(seq, data);
        }

        synchronized byte[] assembleChunks() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] chunk : chunks.values()) {
                out.writeBytes(chunk);
            }
            return out.toByteArray();
        }
    }
}
