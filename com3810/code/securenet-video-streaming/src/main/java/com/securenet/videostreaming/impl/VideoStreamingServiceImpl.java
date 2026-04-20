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
import java.util.logging.Logger;

/**
 * Implementation of the Video Streaming Service.
 *
 * <p>Recording session metadata (sessionId, deviceId, ownerId, startedAt)
 * is persisted in PostgreSQL so that any VSS instance behind the load
 * balancer can look up active sessions. Chunk data is buffered in-memory
 * during the session (transient by nature — assembled and archived to
 * PostgreSQL when the session closes).
 */
public class VideoStreamingServiceImpl implements VideoStreamingService {

    private static final Logger log = Logger.getLogger(VideoStreamingServiceImpl.class.getName());

    private final StorageGateway storageGateway;

    /**
     * In-memory chunk buffer keyed by sessionId.
     * Chunks are transient — assembled into a VideoClip on session close.
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

        log.info("[VSS] openRecordingSession: deviceId=" + deviceId + " ownerId=" + ownerId);

        storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> {
                    log.warning("[VSS] openRecordingSession: device not found deviceId=" + deviceId);
                    return new DeviceNotFoundException(deviceId);
                });

        Optional<String> existingSession = storageGateway.findActiveSessionForDevice(deviceId);
        if (existingSession.isPresent()) {
            log.warning("[VSS] openRecordingSession: session already active for deviceId="
                    + deviceId + " sessionId=" + existingSession.get());
            throw new IllegalStateException(
                    "Recording session already active for device: " + deviceId);
        }

        String sessionId = "rec-" + UUID.randomUUID();
        Instant now = Instant.now();

        storageGateway.saveRecordingSession(sessionId, deviceId, ownerId, now);
        sessionChunks.put(sessionId, new TreeMap<>());

        log.info("[VSS] Recording session opened: sessionId=" + sessionId
                + " deviceId=" + deviceId + " ownerId=" + ownerId);
        return sessionId;
    }

    @Override
    public void closeRecordingSession(String recordingSessionId)
            throws IllegalArgumentException {
        requireNonBlank(recordingSessionId, "recordingSessionId");

        log.info("[VSS] closeRecordingSession: sessionId=" + recordingSessionId);

        Map<String, String> session = storageGateway.findRecordingSession(recordingSessionId)
                .orElseThrow(() -> {
                    log.warning("[VSS] closeRecordingSession: unknown session sessionId="
                            + recordingSessionId);
                    return new IllegalArgumentException(
                            "Unknown or already closed session: " + recordingSessionId);
                });

        String deviceId  = session.get("deviceId");
        String ownerId   = session.get("ownerId");
        Instant startedAt = Instant.parse(session.get("startedAt"));

        storageGateway.deleteRecordingSession(recordingSessionId);

        TreeMap<Long, byte[]> chunks = sessionChunks.remove(recordingSessionId);
        byte[] assembledBytes;
        if (chunks != null && !chunks.isEmpty()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] chunk : chunks.values()) {
                out.writeBytes(chunk);
            }
            assembledBytes = out.toByteArray();
            log.info("[VSS] Assembled " + chunks.size() + " chunks = "
                    + assembledBytes.length + " bytes for sessionId=" + recordingSessionId);
        } else {
            assembledBytes = new byte[0];
            log.warning("[VSS] No chunks for sessionId=" + recordingSessionId
                    + " — skipping archive");
        }

        if (assembledBytes.length == 0) return;

        Duration duration = Duration.between(startedAt, Instant.now());
        try {
            VideoClip clip = archiveFootage(deviceId, ownerId, startedAt, assembledBytes);
            log.info("[VSS] Session archived: sessionId=" + recordingSessionId
                    + " clipId=" + clip.clipId()
                    + " bytes=" + assembledBytes.length
                    + " duration=" + duration.toSeconds() + "s");
        } catch (Exception e) {
            log.severe("[VSS] Failed to archive session: sessionId=" + recordingSessionId
                    + " error=" + e.getMessage());
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

        storageGateway.findRecordingSession(recordingSessionId)
                .orElseThrow(() -> {
                    log.warning("[VSS] ingestChunk: unknown session sessionId="
                            + recordingSessionId);
                    return new IllegalArgumentException(
                            "Unknown or closed session: " + recordingSessionId);
                });

        sessionChunks.computeIfAbsent(recordingSessionId, k -> new TreeMap<>())
                .put(sequenceNumber, chunkBytes);

        log.fine("[VSS] Chunk ingested: sessionId=" + recordingSessionId
                + " seq=" + sequenceNumber + " bytes=" + chunkBytes.length);
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

        log.info("[VSS] archiveFootage: deviceId=" + deviceId + " bytes=" + rawBytes.length);

        storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> {
                    log.warning("[VSS] archiveFootage: device not found deviceId=" + deviceId);
                    return new DeviceNotFoundException(deviceId);
                });

        String clipId      = "clip-" + UUID.randomUUID();
        String storageKey  = "video/" + deviceId + "/" + clipId;
        Duration duration  = Duration.between(segmentStarted, Instant.now());

        VideoClip clip = new VideoClip(
                clipId, deviceId, ownerId, segmentStarted,
                duration, rawBytes.length, storageKey);

        storageGateway.saveVideoClip(clip, rawBytes);
        log.info("[VSS] Footage archived: clipId=" + clipId + " deviceId=" + deviceId
                + " storageKey=" + storageKey + " bytes=" + rawBytes.length
                + " duration=" + duration.toSeconds() + "s");
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

        log.info("[VSS] findClips: deviceId=" + deviceId + " from=" + from + " to=" + to);

        storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        List<VideoClip> clips = storageGateway.findClipsByDevice(deviceId, from, to);
        log.info("[VSS] findClips: found " + clips.size() + " clips for deviceId=" + deviceId);
        return clips;
    }

    @Override
    public String generateSignedPlaybackUrl(String clipId, int validForSeconds)
            throws VideoNotFoundException, IllegalArgumentException {
        requireNonBlank(clipId, "clipId");
        if (validForSeconds < 1 || validForSeconds > 3600) {
            throw new IllegalArgumentException("validForSeconds must be between 1 and 3600");
        }

        log.info("[VSS] generateSignedPlaybackUrl: clipId=" + clipId
                + " validForSeconds=" + validForSeconds);

        VideoClip clip = storageGateway.findClipById(clipId)
                .orElseThrow(() -> {
                    log.warning("[VSS] generateSignedPlaybackUrl: clip not found clipId=" + clipId);
                    return new VideoNotFoundException(clipId);
                });

        long expiresAt    = System.currentTimeMillis() + (validForSeconds * 1000L);
        String signature  = generateSignature(clip.storageKey(), expiresAt);
        String url        = "/playback/" + clipId + "?expires=" + expiresAt + "&sig=" + signature;

        log.info("[VSS] Signed URL generated: clipId=" + clipId + " expiresAt=" + expiresAt);
        return url;
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