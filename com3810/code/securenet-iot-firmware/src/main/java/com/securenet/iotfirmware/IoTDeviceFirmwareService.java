package com.securenet.iotfirmware;



import java.util.Map;

/**
 * API for the on-device firmware layer running on SecureNet IoT devices
 * (cameras, smart locks, motion sensors).
 *
 * <p>This interface defines the operations that the SecureNet cloud platform
 * can invoke on a device, and the operations the device firmware must perform
 * during its lifecycle. In the C4 Container Diagram, the IoT Device Firmware
 * is an external system that communicates with the cloud over
 * HTTPS/REST (for registration and events) and MQTT (for commands).
 *
 * <p>It's responsibilities are:
 * <p>Onboarding— connect to WiFi, register with the Device Management Service, and receive MQTT credentials.
 * <p>Heartbeat — send a keep-alive signal to the Device Management Service at regular intervals so the cloud can detect unresponsive devices.
 * <p>Event publishing — push security events (motion, lock state changes, connectivity changes) to the Event Processing Service over HTTPS/REST.
 * <p>Command handling — receive and execute lock/unlock and stream-start commands delivered via MQTT.
 * <p>Local buffering — record video and events locally when WiFi is unavailable, then sync buffered data when connectivity is restored.
 * <p>Firmware update — download and apply firmware updates pushed by the Device Management Service.
 *
 * <p>Connectivity
 * HTTPS/REST for registration, heartbeats, event publishing, and firmware
 * downloads; MQTT for receiving cloud-initiated commands.
 */
public interface IoTDeviceFirmwareService {

    // =========================================================================
    // Onboarding lifecycle
    // =========================================================================

    /**
     * Initiates the WiFi connection sequence and blocks until the device is
     * connected or all retry attempts are exhausted.
     *
     * Uses exponential back-off between connection attempts to avoid
     * overwhelming the wireless access point.
     *
     * @param ssid     the WiFi network name to connect to
     * @param password the WiFi password
     * @return {@code true} if the connection was established successfully
     */
    boolean connectToWifi(String ssid, String password);

    /**
     * Registers this device with the SecureNet Device Management Service over
     * HTTPS/REST and stores the returned DeviceCredentials in
     * non-volatile memory.
     *
     * <p>Must be called exactly once after a successful
     * {@link #connectToWifi} call, before any other cloud communication.
     *
     * @param registrationToken the one-time token embedded in the QR code
     *                          printed on the device
     * @return the DeviceCredentials (MQTT broker URL, client ID,
     *username, password) to be persisted and used for all subsequent MQTT connections
     * @throws IllegalStateException    if the device is already registered
     * @throws IllegalArgumentException if the registration token is invalid
     *                                  or has already been consumed
     */
    DeviceCredentials registerWithCloud(String registrationToken) throws IllegalStateException, IllegalArgumentException;

    /**
     * Opens a persistent MQTT connection to the SecureNet broker using the
     * obtained during registration.
     *
     * <p>Once connected, the firmware subscribes to its dedicated command topic
     * and begins processing incoming commands.
     *
     * @param credentials the credentials to authenticate with the broker
     * @throws IllegalArgumentException if credentials are malformed or expired
     */
    void connectToMqttBroker(DeviceCredentials credentials) throws IllegalArgumentException;

    // =========================================================================
    // Heartbeat
    // =========================================================================

    /**
     * Sends a heartbeat signal to the Device Management Service over HTTPS/REST.
     *
     * <p>Must be called on a fixed schedule (default: every 30 seconds). If the
     * device fails to send a heartbeat within the cloud's threshold window, its
     * status transitions to UNRESPONSIVE.
     */
    void sendHeartbeat();

    // =========================================================================
    // Event publishing
    // =========================================================================

    /**
     * Publishes a security event to the Event Processing Service over HTTPS/REST.
     *
     * <p>If WiFi is currently unavailable, the event is written to the local
     * buffer for later delivery (see {@link #syncBufferedData}).
     *
     * @param type     the category of security event detected
     * @param metadata event-type-specific key-value pairs (e.g.
     *                 {@code "thumbnailPath"} for motion events)
     */
    void publishEvent(EventType type, Map<String, String> metadata);

    // =========================================================================
    // Command handling (MQTT)
    // =========================================================================

    /**
     * Processes an incoming MQTT command received from the Device Management
     * Service.
     *
     * <p>The firmware MQTT client calls this whenever a message arrives on the
     * device's command topic. Supported command types include:
     * <p>
     * <p>{@code "LOCK"}   — engage the smart lock motor
     * <p>{@code "UNLOCK"} — disengage the smart lock motor
     * <p>{@code "STREAM_START"} — begin pushing a video stream to the supplied target URL
     * <p>{@code "STREAM_STOP"} — halt the active video stream
     * <p>{@code "FIRMWARE_UPDATE"} — download and apply a firmware update
     * <p>
     *
     * @param commandType  the command identifier (e.g. {@code "LOCK"})
     * @param commandParams command-specific parameters (e.g. {@code "streamTargetUrl"} for {@code STREAM_START})
     * @return {@code true} if the command was executed successfully and an acknowledgement was published back to the broker
     */
    boolean handleCommand(CommandType commandType, Map<String, String> commandParams);

    // =========================================================================
    // Local buffering and sync
    // =========================================================================

    /**
     * Buffers a video segment or event payload to local non-volatile storage for later cloud sync.
     *
     * <p>Called automatically when {@link #publishEvent} detects that WiFi is unavailable,
     * and by the camera subsystem when connectivity is lost during recording.
     *
     * @param dataType one of {@code "EVENT"} or {@code "VIDEO_SEGMENT"}
     * @param payload  the raw bytes to buffer
     */
    void bufferLocally(Enum dataType, byte[] payload);

    /**
     * Uploads all locally buffered data to the cloud after WiFi connectivity is restored.
     *
     * <p>Events are delivered to the Event Processing Service and video segments
     * are delivered to the Video Streaming Service. Called automatically on reconnect.
     *
     * @return the number of buffered items successfully delivered
     */
    int syncBufferedData();

    // =========================================================================
    // Firmware update
    // =========================================================================

    /**
     * Downloads and applies a firmware update.
     *
     * <p>The device verifies the digital signature of the downloaded binary before flashing.
     * On success the device reboots and reports the new firmware version in its next heartbeat.
     *
     * @param firmwareVersion the version string to download and install
     * @param downloadUrl     HTTPS URL from which to fetch the firmware binary
     * @return {@code true} if the update was applied successfully
     * @throws IllegalArgumentException if the downloaded binary fails signature verification
     */
    boolean applyFirmwareUpdate(String firmwareVersion, String downloadUrl)
            throws IllegalArgumentException;
}
