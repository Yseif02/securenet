package com.securenet.gateway;

import java.time.Instant;
import java.util.List;

/**
 * Public API of the SecureNet Client Application.
 *
 * <p>The Client Application is the primary interface between the homeowner and
 * the SecureNet platform. All requests to backend services are routed through the API
 * Gateway over HTTPS/JSON, with the exception of video chunks which are
 * streamed directly from the Video Streaming Service for efficiency.
 *
 * <p>It's responsibilities are:</p>
 * - Session management — handle login, logout, and token storage; present the
 *   login screen when a token is missing or expired.
 * <p> - Device dashboard — display live device status, request device commands
 *   (lock/unlock, stream start/stop), and surface the onboarding flow for new
 *   devices.
 * <p> - Event timeline — fetch and display the homeowner's security event history
 *   across all devices.
 * <p> - Video — initialize the player for live streams and archived clip playback;
 *   handle adaptive quality changes transparently.
 * <p> - Push token registration — register the OS-issued APNs or FCM token with
 *   the Notification Service on first launch and whenever the OS issues a new one.
 * <p> - Offline handling — display cached device state with a warning indicator
 *   when the cloud is unreachable; re-sync when connectivity is restored.
 *
 * <p>Callers:</p>
 * Homeowner — interacts directly via the UI.
 *
 * <p>Protocol:</p>
 * HTTPS/JSON to API Gateway for all API calls; direct HTTPS for video chunks
 * from the Video Streaming Service.
 */
public interface ClientApplicationService {

    // =========================================================================
    // Session management
    // =========================================================================

    /**
     * Authenticates the homeowner with their email and password, stores the
     * returned bearer token for use in all subsequent requests, and navigates
     * to the device dashboard.
     *
     * @param email       the homeowner's registered email address
     * @param rawPassword the plaintext password entered by the homeowner
     * @throws AuthenticationException if the credentials are invalid or the
     *                                 account has been suspended
     */
    void login(String email, String rawPassword) throws AuthenticationException;

    /**
     * Revokes the current session token via the API Gateway and clears all
     * locally cached credentials, returning the homeowner to the login screen.
     */
    void logout();

    /**
     * Registers the OS-issued push token with the Notification Service so that
     * security alerts are delivered to this device.
     *
     * <p>Called automatically on first launch and whenever the OS issues a new
     * token. The platform is inferred from the runtime environment
     * ({@code "APNS"} on iOS, {@code "FCM"} on Android/web).
     *
     * @param pushToken the APNs or FCM device token supplied by the OS
     */
    void registerPushToken(String pushToken);

    // =========================================================================
    // Device dashboard
    // =========================================================================

    /**
     * Fetches the list of devices registered to the authenticated homeowner
     * and refreshes the dashboard UI.
     *
     * <p>Called on dashboard load and after any device status change. If the
     * cloud is unreachable, the last cached device list is displayed with a
     * warning indicator.
     *
     * @return an unmodifiable list of DeviceSummary records for display;
     *         empty if the homeowner has no registered devices
     */
    List<DeviceSummary> loadDashboard();

    /**
     * Initiates the device onboarding flow: prompts the homeowner to scan a
     * QR code or enter a device ID manually, then sends a registration request
     * to the API Gateway.
     *
     * @param qrPayloadOrDeviceId the raw QR code string or manually entered
     *                            device identifier
     * @param deviceType          the hardware category selected by the homeowner
     * @throws IllegalArgumentException if the QR payload is malformed or the
     *                                  device ID format is invalid
     */
    void startDeviceOnboarding(String qrPayloadOrDeviceId, String deviceType) throws IllegalArgumentException;

    /**
     * Sends a lock command for the specified smart lock to the API Gateway
     * and updates the UI to reflect the pending and then confirmed state.
     *
     * @param deviceId the smart lock to lock
     * @throws DeviceOfflineException if the device is currently offline or
     *                                unresponsive
     */
    void sendLockCommand(String deviceId) throws DeviceOfflineException;

    /**
     * Sends an unlock command for the specified smart lock to the API Gateway
     * and updates the UI to reflect the pending and then confirmed state.
     *
     * @param deviceId the smart lock to unlock
     * @throws DeviceOfflineException if the device is currently offline or
     *                                unresponsive
     */
    void sendUnlockCommand(String deviceId) throws DeviceOfflineException;

    // =========================================================================
    // Event timeline
    // =========================================================================

    /**
     * Fetches security events for a specific device within a time window and
     * renders them in the device's event timeline view.
     *
     * @param deviceId  the device whose events to display
     * @param from      inclusive start of the time window (UTC)
     * @param to        inclusive end of the time window (UTC)
     * @param maxEvents maximum number of events to fetch (1–500)
     * @return an unmodifiable list of EventSummary records for display;
     *         newest first
     */
    List<EventSummary> loadEventTimeline(String deviceId, Instant from, Instant to, int maxEvents);

    // =========================================================================
    // Video
    // =========================================================================

    /**
     * Requests a live stream for the given camera from the API Gateway,
     * initializes the video player, and begins polling for chunks from the
     * Video Streaming Service.
     *
     * <p>Before sending the request the application checks cloud availability;
     * if the cloud is unreachable it shows a cached status warning instead.
     *
     * @param deviceId the camera to stream
     * @throws DeviceOfflineException if the device is currently offline
     * @throws CloudUnavailableException if the cloud service cannot be reached
     */
    void startLiveStream(String deviceId) throws DeviceOfflineException, CloudUnavailableException;

    /**
     * Stops the active live stream session and releases the video player.
     *
     * @param streamSessionId the session identifier returned when the stream started
     */
    void stopLiveStream(String streamSessionId);

    /**
     * Requests archived clips for a camera within a time range, retrieves
     * signed playback URLs from the Video Streaming Service, and initializes
     * the player for playback.
     *
     * @param deviceId the camera to browse
     * @param from     inclusive start of the time window (UTC)
     * @param to       inclusive end of the time window (UTC)
     * @throws VideoNotFoundException if no footage exists for the given window
     */
    void openVideoPlayback(String deviceId, Instant from, Instant to) throws VideoNotFoundException;

    /**
     * Handles an adaptive bitrate quality change during streaming or playback.
     *
     * <p>Called automatically by the video player when bandwidth changes.
     * Updates the UI to reflect the current quality tier and notifies the
     * homeowner if quality is degraded or restored.
     *
     * @param streamSessionId the active stream or playback session
     * @param bandwidthKbps   the client's current estimated bandwidth in Kbps
     */
    void onBandwidthChange(String streamSessionId, int bandwidthKbps);

    // =========================================================================
    // Offline / connectivity handling
    // =========================================================================

    /**
     * Called by the network layer when cloud connectivity is lost.
     *
     * <p>Switches the dashboard to display cached device state with a warning
     * indicator and disables remote-control actions until connectivity is
     * restored.
     */
    void onCloudConnectionLost();

    /**
     * Called by the network layer when cloud connectivity is restored.
     *
     * <p>Re-fetches live device state, clears warning indicators, and
     * re-enables remote-control actions.
     */
    void onCloudConnectionRestored();

    // =========================================================================
    // Inner exception types
    // =========================================================================

    /** Thrown when a command is sent to a device that is offline or unresponsive. */
    class DeviceOfflineException extends Exception {
        public DeviceOfflineException(String deviceId) {
            super("Device is offline or unresponsive: " + deviceId);
        }
    }

    /** Thrown when the cloud service cannot be reached. */
    class CloudUnavailableException extends Exception {
        public CloudUnavailableException() {
            super("Cloud service is currently unavailable");
        }
    }

    /** Thrown when authentication credentials are invalid. */
    class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }
    }

    /** Thrown when no video footage exists for the requested window. */
    class VideoNotFoundException extends Exception {
        public VideoNotFoundException(String message) {
            super(message);
        }
    }
}