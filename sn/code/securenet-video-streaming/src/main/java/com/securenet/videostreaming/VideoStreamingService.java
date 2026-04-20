package com.securenet.videostreaming;

import com.securenet.model.VideoClip;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.VideoNotFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Public API of the SecureNet Video Streaming Service (VSS).
 *
 * <p>The VSS manages all video data in the platform: receiving encoded video
 * chunks forwarded by the IoT Device Firmware Service (IDFS), archiving
 * footage to the Data Storage Layer, and generating short-lived signed URLs
 * that allow the Client Application to play back historical clips directly —
 * without routing raw video bytes through the API Gateway.
 *
 * <p>Live streaming is not supported. All video is captured by motion-triggered
 * recording sessions, archived immediately, and made available for historical
 * playback once the session ends.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><strong>Chunk ingestion:</strong> accept encoded video chunks
 *       forwarded by the { com.securenet.iotfirmware.IoTDeviceFirmwareService}
 *       over HTTPS/REST. The physical camera sends chunks over MQTT to the IDFS,
 *       which relays them in sequence-number order to this service. The VSS
 *       buffers and assembles contiguous chunks into video segments for
 *       archival.</li>
 *   <li><strong>Recording session lifecycle:</strong> open a recording session
 *       when motion is detected and close it when the DMS confirms motion has
 *       cleared, assembling and persisting all buffered chunks as a
 *       {@link VideoClip} record.</li>
 *   <li><strong>Footage archiving:</strong> persist completed video segments
 *       to the Data Storage Layer and create {@link VideoClip} metadata records
 *       that the Client Application can discover and play back.</li>
 *   <li><strong>Signed URL generation:</strong> produce time-limited HTTPS
 *       URLs for secure client-side clip playback without exposing raw storage
 *       credentials.</li>
 * </ul>
 *
 * <h2>Callers</h2>
 * <ul>
 *   <li>{com.securenet.gateway.APIGatewayService} — routes clip-listing
 *       and playback URL requests from the Client Application.</li>
 *   <li>{com.securenet.iotfirmware.IoTDeviceFirmwareService} — forwards
 *       video chunks received from camera firmware over MQTT.</li>
 *   <li>{com.securenet.devicemanagement.DeviceManagementService} — opens
 *       and closes recording sessions in response to motion events.</li>
 *   <li>Client Applications — play back historical clips using signed URLs
 *       served directly from the Data Storage Layer, bypassing the API Gateway
 *       for all video bytes.</li>
 * </ul>
 *
 * <h2>Protocol</h2>
 * <p>HTTPS/REST for all inbound calls (chunk ingestion, session lifecycle,
 * clip queries, and signed URL generation). Video bytes are delivered to the
 * Client Application directly via signed HTTPS URLs; no video bytes pass
 * through the API Gateway.
 */
public interface VideoStreamingService {

    // =========================================================================
    // Recording session lifecycle
    // =========================================================================

    /**
     * Opens a new recording session for the given camera device.
     *
     * <p>Called by the {com.securenet.devicemanagement.DeviceManagementService}
     * when a motion event is detected and the camera should begin capturing
     * footage. The returned {@code recordingSessionId} is included in the
     * STREAM_START MQTT command sent to the camera (via the IDFS) so the
     * camera firmware can embed it in every video chunk it publishes. The IDFS
     * then includes the {@code recordingSessionId} in each {@link #ingestChunk}
     * call so the VSS can route chunks to the correct session.
     *
     * @param deviceId the camera that will capture the footage
     * @param ownerId  the homeowner who owns this footage
     * @return a recording session identifier that must be embedded in the
     *         STREAM_START command sent to the camera and passed by the IDFS
     *         in all subsequent {@link #ingestChunk} calls for this session
     * @throws DeviceNotFoundException if the camera is not in the device registry
     * @throws IllegalStateException   if a recording session is already active
     *                                 for this device
     */
    String openRecordingSession(String deviceId, String ownerId)
            throws DeviceNotFoundException, IllegalStateException;

    /**
     * Closes an active recording session, assembles all buffered chunks into
     * a contiguous video segment, persists it to the Data Storage Layer, and
     * creates a {@link VideoClip} record for historical playback.
     *
     * <p>Called by the DMS when the motion-check phase confirms all triggered
     * sensors have returned no-motion and the grace period has elapsed. After
     * this call no further chunks are accepted for the session. If a chunk-gap
     * was detected during ingestion, multiple {@link VideoClip} records are
     * created — one per contiguous chunk range — so each gap produces a
     * distinct playable clip rather than a corrupt one.
     *
     * @param recordingSessionId the session identifier returned by
     *                           {@link #openRecordingSession}
     * @throws IllegalArgumentException if the session ID is not recognised or
     *                                  the session has already been closed
     */
    void closeRecordingSession(String recordingSessionId) throws IllegalArgumentException;

    // =========================================================================
    // Chunk ingestion
    // =========================================================================

    /**
     * Accepts an encoded video chunk forwarded by the IDFS and appends it to
     * the active recording session's buffer.
     *
     * <p>Called by the {com.securenet.iotfirmware.IoTDeviceFirmwareService}
     * for every video chunk it receives from the camera's MQTT video topic.
     * The VSS uses {@code sequenceNumber} to detect gaps caused by IDFS buffer
     * overflow or network loss, and to write chunks in the correct order even
     * if HTTPS delivery is slightly out of sequence.
     *
     * <p>If a gap is detected (the received {@code sequenceNumber} is not
     * exactly one greater than the last accepted number), the VSS marks the
     * gap position so that {@link #closeRecordingSession} can split the footage
     * into separate {@link VideoClip} records at each gap boundary.
     *
     * @param recordingSessionId the session identifier issued by
     *                           {@link #openRecordingSession} and forwarded by
     *                           the IDFS with every chunk
     * @param sequenceNumber     monotonically increasing chunk index within the
     *                           session, as assigned by the camera firmware and
     *                           preserved by the IDFS; used to detect and
     *                           record gap positions
     * @param chunkBytes         the raw encoded video bytes for this chunk
     * @throws IllegalArgumentException if the session ID is not recognised or
     *                                  the session is not open
     */
    void ingestChunk(String recordingSessionId, long sequenceNumber, byte[] chunkBytes)
            throws IllegalArgumentException;

    // =========================================================================
    // Footage archiving
    // =========================================================================

    /**
     * Archives a completed video segment to the Data Storage Layer and returns
     * a {@link VideoClip} metadata record.
     *
     * <p>Called internally by {@link #closeRecordingSession} for each
     * contiguous chunk range in the session. A session with no gaps produces
     * one call; each detected gap produces an additional call so that every
     * returned {@link VideoClip} is a self-contained, playable unit.
     *
     * @param deviceId       the camera that captured the footage
     * @param ownerId        the homeowner who owns the footage
     * @param segmentStarted UTC instant at which recording of this segment
     *                       began, derived from the timestamp of the first
     *                       chunk in the range
     * @param rawBytes       the raw encoded video bytes assembled from the
     *                       contiguous chunks in this segment
     * @return the persisted {@link VideoClip} record, including the
     * {@code clipId} used by {@link #generateSignedPlaybackUrl}
     * @throws DeviceNotFoundException if the device is not in the registry
     */
    VideoClip archiveFootage(String deviceId,
                             String ownerId,
                             Instant segmentStarted,
                             byte[] rawBytes)
            throws VideoNotFoundException, DeviceNotFoundException;

    // =========================================================================
    // Playback
    // =========================================================================

    /**
     * Finds all archived clips for a given camera within a time window.
     *
     * <p>Called by the API Gateway when the homeowner navigates to a camera's
     * history view and selects a time range. Results are read from the async
     * standby replica of the Data Storage Layer to protect primary write
     * throughput; slightly stale clip metadata is acceptable for historical
     * queries (CAP: availability over consistency).
     *
     * @param deviceId the camera to query
     * @param from     inclusive start of the time window (UTC)
     * @param to       inclusive end of the time window (UTC)
     * @return an unmodifiable list of {@link VideoClip} records ordered by
     *         segment start time (oldest first); empty if no footage exists in
     *         the window
     * @throws DeviceNotFoundException  if the device is not in the registry
     * @throws IllegalArgumentException if {@code to} is before {@code from}
     */
    List<VideoClip> findClips(String deviceId, Instant from, Instant to)
            throws DeviceNotFoundException, IllegalArgumentException;

    /**
     * Generates a short-lived signed HTTPS URL for direct client-side playback
     * of a specific archived clip.
     *
     * <p>The URL encodes a time-limited access credential signed by the VSS.
     * Clients must request a fresh URL before the previous one expires rather
     * than caching it indefinitely. The Client Application detects a 403 on a
     * chunk request (expired URL) and silently fetches a replacement URL before
     * resuming playback.
     *
     * @param clipId          the unique identifier of the clip to stream
     * @param validForSeconds how long the URL should remain valid (1–3600
     *                        seconds)
     * @return a signed HTTPS URL the Client Application can use to stream the
     *         clip directly from the Data Storage Layer, bypassing the API
     *         Gateway
     * @throws VideoNotFoundException   if no clip with {@code clipId} exists
     * @throws IllegalArgumentException if {@code validForSeconds} is outside
     *                                  the range [1, 3600]
     */
    String generateSignedPlaybackUrl(String clipId, int validForSeconds)
            throws VideoNotFoundException, IllegalArgumentException;

}