package com.securenet.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public API of the SecureNet Data Storage Layer.
 *
 * <p>This service is the single gateway through which all other SecureNet
 * services (except user auth) read and write persistent data. Backed by PostgreSQL,
 * it stores five logically separate domains that share a single database cluster:</p>
 *
 * - User data — homeowner accounts and push-token registrations(written by User Management Service and Notification Service).
 * <p> - Device state — the device registry, credentials, firmware metadata, and heartbeat timestamps (written by Device Management Service).
 * <p> - Events - the time-ordered security event history (written by Event Processing Service).
 * <p> - Video archives — clip metadata and raw video-segment bytes (written by Video Streaming Service).
 * <p> - Device firmware binaries — versioned firmware files for each device type (written during firmware release processes).
 *
 * <p>Callers and their access patterns:</p>
 * <table border="1">
 *   <tr><th>Caller</th><th>Domain(s)</th><th>Label in C4 diagram</th></tr>
 *   <tr><td>User Management Service</td><td>User data</td>
 *       <td>Reads/writes User data [SQL/JDBC]</td></tr>
 *   <tr><td>Notification Service</td><td>Device data / firmware</td>
 *       <td>Reads/writes Device data / firmware [SQL/JDBC]</td></tr>
 *   <tr><td>Event Processing Service</td><td>Events</td>
 *       <td>Reads/writes Events [SQL/JDBC]</td></tr>
 *   <tr><td>Video Streaming Service</td><td>Video archives</td>
 *       <td>Reads/writes Footage [SQL/JDBC]</td></tr>
 *   <tr><td>Device Management Service</td><td>Device state, firmware</td>
 *       <td>Reads/writes Device data / firmware [SQL/JDBC]</td></tr>
 * </table>
 *
 * <p>Protocol:</p>
 * SQL over JDBC.
 */
public interface StorageService {

    // =========================================================================
    // User data
    // =========================================================================

    /**
     * Persists a new user record.
     *
     * @param user the {@link User} to store; {@code userId} must be unique
     * @throws IllegalArgumentException if a user with the same ID or email
     *                                  already exists
     */
    void saveUser(User user) throws IllegalArgumentException;

    /**
     * Looks up a user by their platform-assigned identifier.
     *
     * @param userId the identifier to search for
     * @return an {@link Optional} containing the {@link User} if found, or
     *         empty if no such user exists
     */
    Optional<User> findUserById(String userId);

    /**
     * Looks up a user by their email address.
     *
     * @param email the email to search for (case-insensitive)
     * @return an {@link Optional} containing the {@link User} if found, or
     *         empty if no such user is registered
     */
    Optional<User> findUserByEmail(String email);

    /**
     * Updates an existing user record.
     *
     * @param user the {@link User} with updated fields; the {@code userId} is
     *             used as the key and must already exist
     * @throws IllegalArgumentException if no user with that ID exists
     */
    void updateUser(User user) throws IllegalArgumentException;

    // =========================================================================
    // Device state
    // =========================================================================

    /**
     * Persists a new device record immediately after onboarding.
     *
     * @param device the {@link Device} to store; {@code deviceId} must be unique
     * @throws IllegalArgumentException if a device with the same ID already exists
     */
    void saveDevice(Device device) throws IllegalArgumentException;

    /**
     * Looks up a single device record by its platform-assigned identifier.
     *
     * @param deviceId the device to look up
     * @return an {@link Optional} containing the {@link Device} if found, or
     *         empty if no such device is registered
     */
    Optional<Device> findDeviceById(String deviceId);

    /**
     * Returns all device records belonging to a given homeowner.
     *
     * @param ownerId the homeowner's user identifier
     * @return an unmodifiable list of {@link Device} records; empty if the
     *         owner has no registered devices
     */
    List<Device> findDevicesByOwner(String ownerId);

    /**
     * Persists an updated device record (status change, firmware version
     * upgrade, last-seen timestamp, etc.).
     *
     * @param device the updated {@link Device}; the {@code deviceId} is the key
     * @throws DeviceNotFoundException if no device with that ID exists in storage
     */
    void updateDevice(Device device) throws DeviceNotFoundException;

    /**
     * Permanently deletes a device record and all associated data.
     *
     * @param deviceId the device to delete
     * @throws DeviceNotFoundException if no device with that ID exists
     */
    void deleteDevice(String deviceId) throws DeviceNotFoundException;

    // =========================================================================
    // Security event history
    // =========================================================================

    /**
     * Persists an enriched security event to the event history.
     *
     * @param event the {@link SecurityEvent} to store; {@code eventId} must be unique
     */
    void saveEvent(SecurityEvent event);

    /**
     * Returns a single event by its platform-assigned identifier.
     *
     * @param eventId the event to retrieve
     * @return an {@link Optional} containing the event if found, or empty
     */
    Optional<SecurityEvent> findEventById(String eventId);

    /**
     * Retrieves security events for a specific device within a time window,
     * ordered by occurrence time (most recent first).
     *
     * @param deviceId  the device whose events to retrieve
     * @param from      inclusive start of the time window (UTC)
     * @param to        inclusive end of the time window (UTC)
     * @param maxEvents maximum number of results to return (1–500)
     * @return an unmodifiable list of matching {@link SecurityEvent}s; empty if none
     */
    List<SecurityEvent> findEventsByDevice(String deviceId, Instant from, Instant to, int maxEvents);

    /**
     * Retrieves security events for a homeowner across all their devices,
     * filtered by event type, ordered by occurrence time (most recent first).
     *
     * @param ownerId   the homeowner's identifier
     * @param eventType string representation of the
     *                  {@link com.securenet.model.EventType} to filter by
     * @param from      inclusive start of the time window (UTC)
     * @param to        inclusive end of the time window (UTC)
     * @param maxEvents maximum number of results to return (1–500)
     * @return an unmodifiable list of matching {@link SecurityEvent}s; empty if none
     */
    List<SecurityEvent> findEventsByOwnerAndType(String ownerId, String eventType, Instant from, Instant to, int maxEvents);

    // =========================================================================
    // Video archive
    // =========================================================================

    /**
     * Persists a {@link VideoClip} metadata record and stores the raw video
     * bytes in the backing video archive.
     *
     * @param clip     the clip metadata to persist; {@code clipId} must be unique
     * @param rawBytes the encoded video bytes to store under {@code clip.getStorageKey()}
     * @throws IllegalArgumentException if a clip with the same ID already exists
     */
    void saveVideoClip(VideoClip clip, byte[] rawBytes)
            throws IllegalArgumentException;

    /**
     * Retrieves clip metadata for a specific clip identifier.
     *
     * @param clipId the clip to look up
     * @return an {@link Optional} containing the {@link VideoClip} if found
     */
    Optional<VideoClip> findClipById(String clipId);

    /**
     * Returns all clip metadata records for a given camera within a time window,
     * ordered by start time (oldest first).
     *
     * @param deviceId the camera to query
     * @param from     inclusive start of the window (UTC)
     * @param to       inclusive end of the window (UTC)
     * @return an unmodifiable list of matching {@link VideoClip}s; empty if none
     */
    List<VideoClip> findClipsByDevice(String deviceId, Instant from, Instant to);

    /**
     * Streams the raw video bytes for a clip identified by its storage key.
     *
     * <p>The storage key is obtained from {@link VideoClip#storageKey()}.
     * This method is used internally by the Video Streaming Service when
     * generating signed playback URLs or serving chunk requests.
     *
     * @param storageKey the storage key for the clip
     * @return the raw encoded video bytes
     * @throws VideoNotFoundException if no data exists for the given storage key
     */
    byte[] loadVideoBytes(String storageKey) throws VideoNotFoundException;

    // =========================================================================
    // Firmware binaries
    // =========================================================================

    /**
     * Returns the version string of the latest firmware available for a given
     * device type identifier.
     *
     * @param deviceTypeKey string representation of the
     *                      {@link com.securenet.model.DeviceType}
     * @return the latest semantic version string, e.g. {@code "2.1.4"}; or
     *         {@code null} if no firmware has been published for this device type
     */
    String getLatestFirmwareVersion(String deviceTypeKey);

    /**
     * Returns the raw bytes of a specific firmware binary.
     *
     * <p>Called by the Device Management Service when pushing an update to a
     * device, and by device firmware during a self-initiated update check.
     *
     * @param deviceTypeKey   string representation of the device type
     * @param firmwareVersion the exact semantic version to retrieve
     * @return the firmware binary bytes
     * @throws IllegalArgumentException if the specified version does not exist
     *                                  in the firmware store
     */
    byte[] loadFirmwareBinary(String deviceTypeKey, String firmwareVersion)
            throws IllegalArgumentException;

    /**
     * Stores a new firmware binary, making it available for distribution.
     *
     * <p>Called during a firmware release process. If a binary already exists
     * for the given device type and version, it is replaced.
     *
     * @param deviceTypeKey   string representation of the device type
     * @param firmwareVersion the semantic version of the new firmware
     * @param binaryBytes     the firmware binary to store
     */
    void saveFirmwareBinary(String deviceTypeKey, String firmwareVersion,
                            byte[] binaryBytes);

    // =========================================================================
    // Push token registry (used by Notification Service)
    // =========================================================================

    /**
     * Persists a push notification token for a homeowner's device.
     *
     * @param userId   the homeowner's identifier
     * @param token    the APNs or FCM device token
     * @param platform {@code "APNS"} or {@code "FCM"}
     */
    void savePushToken(String userId, String token, String platform);

    /**
     * Removes a push notification token from the registry.
     *
     * @param token the token to remove; silently ignored if not present
     */
    void deletePushToken(String token);

    /**
     * Returns all active push tokens for a homeowner across all platforms.
     *
     * @param userId the homeowner's identifier
     * @return an unmodifiable list of raw token strings; empty if none registered
     */
    List<String> findPushTokensByUser(String userId);
}
