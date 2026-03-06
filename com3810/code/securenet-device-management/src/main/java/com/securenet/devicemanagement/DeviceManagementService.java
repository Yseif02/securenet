package com.securenet.devicemanagement;


import java.util.List;

/**
 * Public API of the SecureNet Device Management Service.
 *
 * <p>This service is the authoritative registry for all IoT devices in the
 * SecureNet platform. It manages the full device lifecycle from onboarding
 * through deregistration, distributes firmware, and dispatches remote commands
 * to individual devices via MQTT.
 *
 * <p>It's responsibilities are:</p>
 *
 * Device registry — create, read, update, and delete device records in the Data Storage Layer (SQL/JDBC).
 * <p>Onboarding — validate QR codes, generate DeviceCredentials, and accept the device's first registration handshake.
 * <p>Heartbeat monitoring — track the last-seen timestampof every device and transition status to
 * UNRESPONSIVE when the threshold is exceeded.
 * <p>Firmware distribution — store firmware binaries and update commands to devices.
 * <p>Remote commands — send lock/unlock, stream-start, and other commands to devices via MQTT.
 *
 * <p>Callers:</p>
 * {@link com.securenet.gateway.APIGatewayService} — routes homeowner-initiated device commands here via HTTPS/REST.
 * <p> IoT Device Firmware — registers, sends heartbeats, and requests firmware via HTTPS/REST.
 * <p>
 * <p>Protocol:</p>
 * HTTPS/REST for inbound calls; MQTT for outbound device commands.
 */
public interface DeviceManagementService {

    // =========================================================================
    // Onboarding
    // =========================================================================

    /**
     * Registers a new IoT device with the SecureNet platform.
     *
     * <p>This is called during the device onboarding workflow after the
     * homeowner scans a QR code or enters a device ID manually. The service
     * creates a device record, generates DeviceCredentials, and stores
     * everything in the Data Storage Layer.
     *
     * @param ownerId    the authenticated user registering this device
     * @param deviceType the hardware category of the device
     * @param qrPayload  the raw string decoded from the device's QR code,
     *                   containing the manufacturer-assigned device identifier
     *                   and a one-time registration token
     * @return the newly created Device in PENDING_REGISTRATION state
     * @throws DeviceAlreadyRegisteredException if the device ID encoded in the
     * QR payload is already present in the registry
     * @throws IllegalArgumentException if the QR payload is malformed or
     * the one-time token has expired
     */
    Device registerDevice(String ownerId, DeviceType deviceType, String qrPayload)
            throws DeviceAlreadyRegisteredException, IllegalArgumentException;

    /**
     * Accepts the device's first HTTPS/REST connection after power-on,
     * completes the onboarding handshake, and returns MQTT broker credentials.
     *
     * <p>After this call succeeds the device transitions to ONLINE and begins sending heartbeats.
     *
     * @param deviceId          the manufacturer-assigned device identifier
     * @param registrationToken the one-time token embedded in the QR code
     * @return DeviceCredentials the device must use to connect to
     *         the MQTT broker
     * @throws DeviceNotFoundException  if no pending registration exists for
     *                                  this device identifier
     * @throws IllegalArgumentException if the registration token is invalid
     *                                  or already consumed
     */
    DeviceCredentials acceptDeviceRegistration(String deviceId, String registrationToken)
            throws DeviceNotFoundException, IllegalArgumentException;

    // =========================================================================
    // Registry queries
    // =========================================================================

    /**
     * Returns the current registry record for a single device.
     *
     * @param deviceId the platform-assigned device identifier
     * @return the Device record
     * @throws DeviceNotFoundException if the device is not in the registry
     */
    Device getDevice(String deviceId) throws DeviceNotFoundException;

    /**
     * Returns all devices registered to a given homeowner, ordered by
     * registration date (most recent first).
     *
     * @param ownerId the authenticated homeowner's user identifier
     * @return an unmodifiable list of Device records; empty if the owner has no registered devices
     */
    List<Device> listDevicesForOwner(String ownerId);

    // =========================================================================
    // Heartbeat and status management
    // =========================================================================

    /**
     * Records a heartbeat received from a device, updating its last-seen
     * timestamp and transitioning its status to
     * ONLINE if it was previously
     * OFFLINE or UNRESPONSIVE.
     *
     * <p>Devices send heartbeats over HTTPS/REST on a configurable interval
     * (default: 30 seconds).
     *
     * @param deviceId the device reporting its heartbeat
     * @throws DeviceNotFoundException if the device is not in the registry
     */
    void recordHeartbeat(String deviceId) throws DeviceNotFoundException;

    /**
     * Transitions a device to UNRESPONSIVE after the heartbeat threshold has been exceeded.
     *
     * <p>Called by the internal heartbeat-monitor background process, not
     * directly by the API Gateway. Triggers an alert via the
     * {@link com.securenet.notification.NotificationService}.
     *
     * @param deviceId the device to mark as unresponsive
     * @throws DeviceNotFoundException if the device is not in the registry
     */
    void markDeviceUnresponsive(String deviceId) throws DeviceNotFoundException;

    /**
     * Updates the status of a device explicitly.
     *
     * @param deviceId  the device whose status should change
     * @param newStatus the status to apply
     * @throws DeviceNotFoundException if the device is not in the registry
     */
    void updateDeviceStatus(String deviceId, DeviceStatus newStatus)
            throws DeviceNotFoundException;

    // =========================================================================
    // Remote commands
    // =========================================================================

    /**
     * Sends a lock command to a smart lock device via MQTT.
     *
     * <p>The command is published to the device's dedicated MQTT command topic.
     * This call blocks until the device sends an acknowledgement or the
     * command timeout elapses.
     *
     * @param deviceId the smart lock to command
     * @return {@code true} if the device acknowledged the lock command,
     *         {@code false} if the command timed out without acknowledgement
     * @throws DeviceNotFoundException if the device is not in the registry
     * @throws DeviceOfflineException  if the device is currently offline or
     *                                 unresponsive
     */
    boolean sendLockCommand(String deviceId)
            throws DeviceNotFoundException, DeviceOfflineException;

    /**
     * Sends an unlock command to a smart lock device via MQTT.
     *
     * @param deviceId the smart lock to command
     * @return {@code true} if the device acknowledged the unlock command
     * @throws DeviceNotFoundException if the device is not in the registry
     * @throws DeviceOfflineException  if the device is currently offline or
     *                                 unresponsive
     */
    boolean sendUnlockCommand(String deviceId)
            throws DeviceNotFoundException, DeviceOfflineException;

    /**
     * Requests the device to begin streaming video to the Video Streaming
     * Service.
     *
     * @param deviceId       the camera to activate
     * @param streamTargetUrl the RTSP/HTTPS endpoint the camera should push
     *                        its stream to
     * @throws DeviceNotFoundException if the device is not in the registry
     * @throws DeviceOfflineException  if the device is currently offline or
     *                                 unresponsive
     */
    void sendStreamStartCommand(String deviceId, String streamTargetUrl)
            throws DeviceNotFoundException, DeviceOfflineException;

    // =========================================================================
    // Firmware management
    // =========================================================================

    /**
     * Returns the version string of the latest available firmware for the
     * given device type.
     *
     * <p>The firmware binary is stored in the Data Storage Layer. The device
     * calls this during onboarding (and periodically) to check for updates.
     *
     * @param deviceType the hardware category to look up
     * @return the latest semantic version string, e.g. {@code "2.1.4"}
     */
    String getLatestFirmwareVersion(DeviceType deviceType);

    /**
     * Pushes a firmware update command to a device, instructing it to download
     * and install the specified firmware version.
     *
     * @param deviceId        the device to update
     * @param firmwareVersion the version the device should install
     * @throws DeviceNotFoundException if the device is not in the registry
     * @throws DeviceOfflineException  if the device cannot be reached
     * @throws IllegalArgumentException if the specified version does not exist
     *                                  in the firmware store
     */
    void pushFirmwareUpdate(String deviceId, String firmwareVersion)
            throws DeviceNotFoundException, DeviceOfflineException,
                   IllegalArgumentException;

    // =========================================================================
    // Deregistration
    // =========================================================================

    /**
     * Permanently removes a device from the SecureNet registry.
     *
     * <p>This revokes the device's MQTT credentials, deletes its registry record,
     * and removes associated data from the Data Storage Layer. The operation
     * cannot be undone.
     *
     * @param deviceId the device to deregister
     * @param ownerId  the authenticated homeowner requesting deregistration;
     *                 must match the device's recorded owner
     * @throws DeviceNotFoundException  if the device is not in the registry
     * @throws IllegalArgumentException if {@code ownerId} does not match the
     *                                  device's recorded owner
     */
    void deregisterDevice(String deviceId, String ownerId)
            throws DeviceNotFoundException, IllegalArgumentException;
}
