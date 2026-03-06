package com.securenet.videostreaming;


import java.time.Instant;
import java.util.List;

/**
 * Public API of the SecureNet Video Streaming Service.
 *
 * <p>This service manages all video data in the platform: ingesting live streams
 * pushed by camera firmware, archiving footage in the Data Storage Layer, and
 * generating short-lived signed URLs that allow the Client Application to
 * play back clips directly — without routing raw video through the API Gateway.
 *
 * <p>It's responsibilities are:</p>
 * - Live stream ingestion</strong> — accept camera streams, buffer chunks,
 * and make them available for real-time viewing. Directly streamedto the
 * Client Application ({@code Streams Video}) bypassing the API Gateway for efficiency.
 *
 * - <p>Footage archiving — persist video segments to the Data Storage Layer and create
 * {@link VideoClip} records.
 *
 * - <p>Adaptive bitrate — detect available bandwidth and transcode to a lower resolution
 * when necessary, restoring quality automatically when bandwidth recovers.
 *
 * - <p>Signed URL generation — produce time-limited URLs for secure client-side playback
 * without exposing raw storage credentials.
 *
 * <p>Callers:</p>
 *  {@link com.securenet.gateway.APIGatewayService} — routes playback and clip-listing requests.
 *   <p>IoT Device Firmware — pushes live RTSP streams directly.
 *   <p>Client Applications — stream video chunks directly (bypasses gateway).
 *
 * <p>Protocol:</p>
 * HTTPS/REST for metadata; direct video streaming for live and playback chunks.
 */
public interface VideoStreamingService {

    // =========================================================================
    // Live streaming
    // =========================================================================

    /**
     * Initiates ingestion of a live stream from a camera device.
     *
     * <p>Called by {@link com.securenet.devicemanagement.DeviceManagementService}
     * after it sends a stream-start command to the device. Returns a stream
     * session identifier that the Client Application uses to request chunks.
     *
     * @param deviceId the camera whose stream is starting
     * @param ownerId  the homeowner authorised to view this stream
     * @return a stream session ID used by the Client Application to request video chunks
     * @throws DeviceNotFoundException  if the camera is not in the registry
     * @throws IllegalStateException    if a stream is already active for this
     *                                  device
     */
    String startLiveStream(String deviceId, String ownerId)
            throws DeviceNotFoundException, IllegalStateException;

    /**
     * Terminates an active live stream session and triggers archival of any
     * buffered footage.
     *
     * @param streamSessionId the session identifier returned by {@link #startLiveStream}
     * @throws IllegalArgumentException if the session ID is not recognised
     */
    void stopLiveStream(String streamSessionId) throws IllegalArgumentException;

    /**
     * Returns the URL of the next available chunk for an active live stream.
     *
     * <p>The Client Application calls this in a polling loop to display the
     * live feed. The service selects HD or reduced-resolution chunks based on
     * the available bandwidth detected for the session.
     *
     * @param streamSessionId  the active session identifier
     * @param bandwidthKbps    the client's current estimated downstream
     * bandwidth in kilobits per second; used to select the appropriate quality tier
     * @return a short-lived HTTPS URL pointing to the next video chunk
     * @throws IllegalArgumentException if the session has ended or is unknown
     */
    String getNextChunkUrl(String streamSessionId, int bandwidthKbps)
            throws IllegalArgumentException;

    // =========================================================================
    // Footage archiving
    // =========================================================================

    /**
     * Archives a completed video segment to the Data Storage Layer and returns
     * a {@link VideoClip} record.
     *
     * <p>Called internally at the end of a live session and when a
     * motion-detection event triggers clip capture.
     *
     * @param deviceId       the camera that captured the footage
     * @param ownerId        the homeowner who owns the footage
     * @param segmentStarted UTC instant at which recording of this segment began
     * @param rawBytes       the raw encoded video bytes to store
     * @return the persisted {@link VideoClip} record
     * @throws DeviceNotFoundException if the device is not in the registry
     */
    VideoClip archiveFootage(String deviceId, String ownerId, Instant segmentStarted, byte[] rawBytes) throws DeviceNotFoundException;

    // =========================================================================
    // Playback
    // =========================================================================

    /**
     * Finds all archived clips for a given camera within a time window.
     *
     * <p>Called by the Client Application when the homeowner navigates to a
     * camera's history view and selects a time range.
     *
     * @param deviceId the camera to query
     * @param from     inclusive start of the time window (UTC)
     * @param to       inclusive end of the time window (UTC)
     * @return an unmodifiable list of {@link VideoClip}s in the window, ordered
     *         by start time (oldest first); empty if no footage exists
     * @throws DeviceNotFoundException  if the device is not in the registry
     * @throws IllegalArgumentException if {@code to} is before {@code from}
     */
    List<VideoClip> findClips(String deviceId, Instant from, Instant to) throws DeviceNotFoundException, IllegalArgumentException;

    /**
     * Generates a short-lived signed HTTPS URL for direct client-side playback
     * of a specific archived clip.
     *
     * <p>The URL encodes a time-limited access credential. Clients must request
     * a fresh URL before the previous one expires rather than caching it.
     *
     * @param clipId          the clip to generate a URL for
     * @param validForSeconds how long the URL should remain valid (1–3600)
     * @return a signed HTTPS URL the client can use to stream the clip
     * @throws VideoNotFoundException   if no clip with this ID exists
     * @throws IllegalArgumentException if {@code validForSeconds} is out of range
     */
    String generateSignedPlaybackUrl(String clipId, int validForSeconds) throws VideoNotFoundException, IllegalArgumentException;

    // =========================================================================
    // Adaptive bitrate
    // =========================================================================

    /**
     * Selects the appropriate quality tier for a streaming session based on the
     * client's reported bandwidth.
     *
     * <p>Called internally before serving each chunk. Returns a quality descriptor
     * that maps to a pre-encoded bitrate profile stored in the archive.
     *
     * <p>Thresholds:
     *
     *   <p> > 2000 Kbps -> {@code "HD"} (1080p)
     *   <p> 500–1999 Kbps -> {@code "SD"} (480p)
     *   <p> < 500 Kbps  -> {@code "LOW"} (240p)
     *
     *
     * @param bandwidthKbps the client's current estimated bandwidth in Kbps
     * @return a quality tier label such as {@code "HD"}, {@code "SD"},
     *         or {@code "LOW"}
     */
    String selectQualityTier(int bandwidthKbps);
}
