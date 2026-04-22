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
 * Implementation of the Video Streaming Service with checkpoint-based
 * chunk durability and VSS failover support.
 *
 * <h3>Chunk durability design</h3>
 * <p>Chunks arrive from IDFS (not directly from the camera). IDFS receives
 * raw chunks over MQTT, buffers them, and flushes batches of 10 to VSS via
 * HTTP. VSS checkpoints every chunk it receives immediately to the
 * {@code vss_pending_chunks} PostgreSQL table ({@code CHECKPOINT_INTERVAL=1}).
 * Once the full batch is persisted, IDFS publishes a stream ack to the
 * camera over MQTT. The camera deletes its local buffer up to that seq.
 *
 * <p>If this VSS instance crashes mid-stream, IDFS performs the resume
 * handshake by calling {@code POST /vss/session/resume} on a healthy VSS
 * via the VSS load balancer. The new instance loads checkpointed chunks
 * from Postgres and continues from there. The camera is unaware of VSS topology.
 *
 * <h3>Session close</h3>
 * <p>On {@link #closeRecordingSession}: all checkpointed chunks are
 * loaded from Postgres, merged with any in-memory chunks not yet
 * flushed, assembled in sequence order, and archived as a
 * {@link VideoClip}.
 */
public class VideoStreamingServiceImpl implements VideoStreamingService {

    private static final Logger log = Logger.getLogger(VideoStreamingServiceImpl.class.getName());

    /**
     * VSS checkpoints every chunk it receives from IDFS.
     * Batching is IDFS's responsibility — it accumulates 10 chunks then
     * sends them one-at-a-time to VSS. VSS just needs to persist each
     * one immediately so the batch is durable before IDFS acks the camera.
     */
    static final int CHECKPOINT_INTERVAL = 1;

    private static final String SIGNING_SECRET = "securenet-vss-signing-key";

    private final StorageGateway storageGateway;

    /**
     * In-memory chunk buffer keyed by sessionId.
     * Only holds chunks since the last checkpoint flush.
     * On failover recovery the buffer is rebuilt from Postgres.
     */
    private final ConcurrentHashMap<String, TreeMap<Long, byte[]>> sessionChunks =
            new ConcurrentHashMap<>();

    /**
     * Tracks the highest sequence number that has been checkpointed to
     * Postgres for each session. -1 means nothing checkpointed yet.
     */
    private final ConcurrentHashMap<String, Long> lastCheckpointedSeq =
            new ConcurrentHashMap<>();

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
        lastCheckpointedSeq.put(sessionId, -1L);

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

        String deviceId   = session.get("deviceId");
        String ownerId    = session.get("ownerId");
        Instant startedAt = Instant.parse(session.get("startedAt"));

        // 1. Load all checkpointed chunks from Postgres
        TreeMap<Long, byte[]> allChunks = storageGateway.loadCheckpointedChunks(recordingSessionId);
        log.info("[VSS] closeRecordingSession: loaded " + allChunks.size()
                + " checkpointed chunks from Postgres for sessionId=" + recordingSessionId);

        // 2. Merge with any in-memory chunks not yet flushed
        TreeMap<Long, byte[]> inMemory = sessionChunks.remove(recordingSessionId);
        if (inMemory != null && !inMemory.isEmpty()) {
            log.info("[VSS] closeRecordingSession: merging " + inMemory.size()
                    + " in-memory chunks for sessionId=" + recordingSessionId);
            allChunks.putAll(inMemory);
        }
        lastCheckpointedSeq.remove(recordingSessionId);

        // 3. Delete the recording session and all checkpointed chunks from Postgres
        storageGateway.deleteRecordingSession(recordingSessionId);
        storageGateway.deleteCheckpointedChunks(recordingSessionId);

        // 4. Assemble and archive
        if (allChunks.isEmpty()) {
            log.warning("[VSS] closeRecordingSession: no chunks for sessionId="
                    + recordingSessionId + " — skipping archive");
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] chunk : allChunks.values()) {
            out.writeBytes(chunk);
        }
        byte[] assembledBytes = out.toByteArray();
        log.info("[VSS] Assembled " + allChunks.size() + " chunks = "
                + assembledBytes.length + " bytes for sessionId=" + recordingSessionId);

        try {
            VideoClip clip = archiveFootage(deviceId, ownerId, startedAt, assembledBytes);
            log.info("[VSS] Session archived: sessionId=" + recordingSessionId
                    + " clipId=" + clip.clipId()
                    + " bytes=" + assembledBytes.length
                    + " duration=" + Duration.between(startedAt, Instant.now()).toSeconds() + "s");
        } catch (Exception e) {
            log.severe("[VSS] Failed to archive session: sessionId=" + recordingSessionId
                    + " error=" + e.getMessage());
        }
    }

    // =====================================================================
    // Chunk ingestion with checkpoint
    // =====================================================================

    /**
     * Ingests a chunk and returns the highest sequence number acked to the
     * camera, or -1 if no checkpoint has been flushed yet this call.
     *
     * <p>Every {@value #CHECKPOINT_INTERVAL} chunks the accumulated batch
     * is flushed to Postgres and this method returns the highest seq in
     * the batch. The camera should delete local chunks up to and including
     * that sequence number.
     *
     * @return the ackedThrough sequence number, or -1 if no checkpoint this call
     */
    public long ingestChunkAndGetAck(String recordingSessionId, long sequenceNumber,
                                     byte[] chunkBytes) throws IllegalArgumentException {
        requireNonBlank(recordingSessionId, "recordingSessionId");
        Objects.requireNonNull(chunkBytes, "chunkBytes");

        // If this VSS doesn't know about the session in memory, it may be
        // a resumed session — load checkpointed chunks from Postgres.
        sessionChunks.computeIfAbsent(recordingSessionId, id -> {
            log.info("[VSS] ingestChunk: session not in memory, loading from Postgres: sessionId=" + id);
            TreeMap<Long, byte[]> recovered = storageGateway.loadCheckpointedChunks(id);
            long maxSeq = recovered.isEmpty() ? -1L : recovered.lastKey();
            lastCheckpointedSeq.put(id, maxSeq);
            log.info("[VSS] ingestChunk: recovered " + recovered.size()
                    + " checkpointed chunks, maxSeq=" + maxSeq + " for sessionId=" + id);
            // We loaded these from Postgres already — keep them in memory so
            // closeRecordingSession merge works, but don't re-checkpoint them.
            return recovered;
        });

        // Validate session still exists in Postgres
        storageGateway.findRecordingSession(recordingSessionId)
                .orElseThrow(() -> {
                    log.warning("[VSS] ingestChunk: unknown session sessionId=" + recordingSessionId);
                    return new IllegalArgumentException(
                            "Unknown or closed session: " + recordingSessionId);
                });

        TreeMap<Long, byte[]> chunks = sessionChunks.get(recordingSessionId);
        chunks.put(sequenceNumber, chunkBytes);

        log.fine("[VSS] Chunk ingested: sessionId=" + recordingSessionId
                + " seq=" + sequenceNumber + " bytes=" + chunkBytes.length);

        // Count total chunks since last checkpoint
        long prevCheckpoint = lastCheckpointedSeq.getOrDefault(recordingSessionId, -1L);
        long chunksInBatch  = chunks.tailMap(prevCheckpoint + 1).size();

        if (chunksInBatch >= CHECKPOINT_INTERVAL) {
            return flushCheckpoint(recordingSessionId, chunks, prevCheckpoint);
        }

        return -1L; // no checkpoint this call
    }

    /**
     * Legacy interface method — delegates to ingestChunkAndGetAck.
     * The HTTP server uses ingestChunkAndGetAck directly to get the ack value.
     */
    @Override
    public void ingestChunk(String recordingSessionId, long sequenceNumber,
                            byte[] chunkBytes) throws IllegalArgumentException {
        ingestChunkAndGetAck(recordingSessionId, sequenceNumber, chunkBytes);
    }

    /**
     * Flushes chunks since the last checkpoint to Postgres.
     *
     * @return the highest sequence number in the flushed batch
     */
    private long flushCheckpoint(String sessionId, TreeMap<Long, byte[]> chunks,
                                 long prevCheckpoint) {
        // Only flush chunks above the previous checkpoint
        TreeMap<Long, byte[]> batch = new TreeMap<>(chunks.tailMap(prevCheckpoint + 1));
        if (batch.isEmpty()) return prevCheckpoint;

        log.info("[VSS] Flushing checkpoint: sessionId=" + sessionId
                + " chunks=" + batch.size()
                + " seqs=[" + batch.firstKey() + ".." + batch.lastKey() + "]");

        for (Map.Entry<Long, byte[]> entry : batch.entrySet()) {
            storageGateway.saveChunkCheckpoint(sessionId, entry.getKey(), entry.getValue());
        }

        long newCheckpoint = batch.lastKey();
        lastCheckpointedSeq.put(sessionId, newCheckpoint);

        log.info("[VSS] Checkpoint flushed: sessionId=" + sessionId
                + " ackedThrough=" + newCheckpoint);
        return newCheckpoint;
    }

    // =====================================================================
    // Resume handshake
    // =====================================================================

    /**
     * Called by a camera that lost its VSS connection and needs to know
     * where to resume sending chunks.
     *
     * <p>Returns the highest sequence number that has been durably
     * checkpointed to Postgres for this session. The camera should
     * resend from {@code resumeFrom + 1} (i.e. everything above its
     * last ack, which it still has in its local buffer).
     *
     * @param sessionId  the recording session ID from the original STREAM_START
     * @param vssUrl     the URL of this VSS instance, returned to the camera
     *                   so it can target this instance for subsequent chunks
     * @return a map with {@code vssUrl} and {@code resumeFrom} (-1 if no checkpoint yet)
     * @throws IllegalArgumentException if the session does not exist
     */
    public Map<String, Object> resumeSession(String sessionId, String vssUrl)
            throws IllegalArgumentException {
        requireNonBlank(sessionId, "sessionId");

        storageGateway.findRecordingSession(sessionId)
                .orElseThrow(() -> {
                    log.warning("[VSS] resumeSession: unknown session sessionId=" + sessionId);
                    return new IllegalArgumentException("Unknown session: " + sessionId);
                });

        long maxSeq = storageGateway.getMaxCheckpointedSeq(sessionId);

        log.info("[VSS] resumeSession: sessionId=" + sessionId
                + " resumeFrom=" + maxSeq + " vssUrl=" + vssUrl);

        // Pre-load checkpointed chunks into memory so this instance is
        // ready to continue accumulating without re-fetching on first chunk
        sessionChunks.computeIfAbsent(sessionId, id -> {
            TreeMap<Long, byte[]> recovered = storageGateway.loadCheckpointedChunks(id);
            lastCheckpointedSeq.put(id, maxSeq);
            log.info("[VSS] resumeSession: pre-loaded " + recovered.size()
                    + " chunks into memory for sessionId=" + id);
            return recovered;
        });

        return Map.of("vssUrl", vssUrl, "resumeFrom", maxSeq);
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

        String clipId     = "clip-" + UUID.randomUUID();
        String storageKey = "video/" + deviceId + "/" + clipId;
        Duration duration = Duration.between(segmentStarted, Instant.now());

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

        long expiresAt   = System.currentTimeMillis() + (validForSeconds * 1000L);
        String signature = generateSignature(clip.storageKey(), expiresAt);
        String url       = "/playback/" + clipId + "?expires=" + expiresAt + "&sig=" + signature;

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