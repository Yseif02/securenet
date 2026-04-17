package com.securenet.devicemanagement.impl;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.devicemanagement.DeviceManagementService;
import com.securenet.model.*;
import com.securenet.model.bootstrap.BootstrapRegistrationResult;
import com.securenet.model.bootstrap.DeviceRegistrationInfo;
import com.securenet.model.bootstrap.FirmwareAssignment;
import com.securenet.model.exception.DeviceAlreadyRegisteredException;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.DeviceOfflineException;
import com.securenet.storage.StorageGateway;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed implementation of the Device Management Service.
 *
 * <p>Runs as its own process and accesses persistent data through
 * {@link StorageGateway} (HTTP client to the remote Storage Service).
 *
 * <p>Remote commands (lock, unlock, stream-start) are dispatched to
 * devices via the IDFS {@code /command} endpoint, which publishes to
 * the device's MQTT command topic and waits for an ack. This completes
 * the full round-trip: DMS → IDFS → MQTT → device → ack → MQTT → IDFS → DMS.
 */
public class DeviceManagementServiceImpl implements DeviceManagementService {

    private final StorageGateway storageGateway;
    private final String idfsBaseUrl;
    private final ServiceClient httpClient;

    /** Pending registration tokens keyed by deviceId. */
    private final Map<String, String> pendingTokens = new ConcurrentHashMap<>();

    /** Last heartbeat timestamp keyed by deviceId. */
    private final Map<String, Instant> lastHeartbeats = new ConcurrentHashMap<>();

    /**
     * @param storageGateway HTTP client pointing to the remote Storage Service
     * @param idfsBaseUrl    base URL of the IDFS server, e.g. {@code "http://localhost:8080"}
     */
    public DeviceManagementServiceImpl(StorageGateway storageGateway, String idfsBaseUrl) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.idfsBaseUrl = Objects.requireNonNull(idfsBaseUrl, "idfsBaseUrl");
        this.httpClient = new ServiceClient();
    }

    // =====================================================================
    // Onboarding
    // =====================================================================

    @Override
    public Device registerDevice(String ownerId, DeviceType deviceType, String qrPayload)
            throws DeviceAlreadyRegisteredException, IllegalArgumentException {
        requireNonBlank(ownerId, "ownerId");
        Objects.requireNonNull(deviceType, "deviceType");

        ParsedQrPayload parsed = parseQrPayload(qrPayload);

        Device existing = storageGateway.findDeviceById(parsed.deviceId()).orElse(null);
        if (existing != null && existing.status() != DeviceStatus.DEREGISTERED) {
            throw new DeviceAlreadyRegisteredException(parsed.deviceId());
        }

        Device device = new Device(
                parsed.deviceId(),
                defaultDisplayName(deviceType, parsed.deviceId()),
                deviceType,
                ownerId,
                DeviceStatus.PENDING_REGISTRATION,
                Instant.now(),
                "1.0.0"
        );

        storageGateway.saveDevice(device);
        pendingTokens.put(parsed.deviceId(), parsed.registrationToken());
        return device;
    }

    @Override
    public BootstrapRegistrationResult acceptDeviceRegistration(String deviceId, String registrationToken)
            throws DeviceNotFoundException, IllegalArgumentException {
        requireNonBlank(deviceId, "deviceId");
        requireNonBlank(registrationToken, "registrationToken");

        Device device = storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        String expectedToken = pendingTokens.get(deviceId);
        if (expectedToken == null) {
            throw new DeviceNotFoundException(deviceId);
        }
        if (!expectedToken.equals(registrationToken)) {
            throw new IllegalArgumentException("Invalid registration token for device: " + deviceId);
        }

        Instant now = Instant.now();

        if (device.status() == DeviceStatus.PENDING_REGISTRATION) {
            try {
                storageGateway.updateDevice(device.withStatus(DeviceStatus.ONLINE));
            } catch (DeviceNotFoundException e) {
                throw new RuntimeException("Device disappeared during registration", e);
            }
            device = storageGateway.findDeviceById(deviceId)
                    .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        }

        lastHeartbeats.put(deviceId, now);

        return buildBootstrapResult(device, now);
    }

    // =====================================================================
    // Registry queries
    // =====================================================================

    @Override
    public Device getDevice(String deviceId) throws DeviceNotFoundException {
        requireNonBlank(deviceId, "deviceId");
        return storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
    }

    @Override
    public List<Device> listDevicesForOwner(String ownerId) {
        requireNonBlank(ownerId, "ownerId");
        return storageGateway.findDevicesByOwner(ownerId);
    }

    // =====================================================================
    // Heartbeat and status
    // =====================================================================

    @Override
    public void recordHeartbeat(String deviceId) throws DeviceNotFoundException {
        Device device = getDevice(deviceId);
        lastHeartbeats.put(deviceId, Instant.now());

        if (device.status() == DeviceStatus.OFFLINE ||
                device.status() == DeviceStatus.UNRESPONSIVE ||
                device.status() == DeviceStatus.PENDING_REGISTRATION) {
            try {
                storageGateway.updateDevice(device.withStatus(DeviceStatus.ONLINE));
            } catch (DeviceNotFoundException e) {
                throw new RuntimeException("Device disappeared during heartbeat", e);
            }
        }
    }

    @Override
    public void markDeviceUnresponsive(String deviceId) throws DeviceNotFoundException {
        Device device = getDevice(deviceId);
        storageGateway.updateDevice(device.withStatus(DeviceStatus.UNRESPONSIVE));
    }

    @Override
    public void updateDeviceStatus(String deviceId, DeviceStatus newStatus)
            throws DeviceNotFoundException {
        Objects.requireNonNull(newStatus, "newStatus");
        Device device = getDevice(deviceId);
        storageGateway.updateDevice(device.withStatus(newStatus));
    }

    // =====================================================================
    // Remote commands — dispatched via IDFS /command → MQTT → device
    // =====================================================================

    /**
     * Sends a LOCK command to a smart lock device.
     *
     * <p>The command flows: DMS → HTTP POST to IDFS /command →
     * IDFS publishes to MQTT topic {@code securenet/devices/{id}/commands/lock} →
     * device receives, actuates lock motor, publishes ack →
     * IDFS receives ack, returns result → DMS returns to caller.
     *
     * @param deviceId the smart lock to command
     * @return {@code true} if the device acknowledged the lock command
     * @throws DeviceNotFoundException if the device is not in the registry
     * @throws DeviceOfflineException  if the device is not reachable
     */
    @Override
    public boolean sendLockCommand(String deviceId)
            throws DeviceNotFoundException, DeviceOfflineException {
        ensureDeviceReachable(deviceId);
        return dispatchCommand(deviceId, "LOCK");
    }

    @Override
    public boolean sendUnlockCommand(String deviceId)
            throws DeviceNotFoundException, DeviceOfflineException {
        ensureDeviceReachable(deviceId);
        return dispatchCommand(deviceId, "UNLOCK");
    }

    @Override
    public void sendStreamStartCommand(String deviceId, String streamTargetUrl)
            throws DeviceNotFoundException, DeviceOfflineException {
        ensureDeviceReachable(deviceId);
        requireNonBlank(streamTargetUrl, "streamTargetUrl");
        dispatchCommand(deviceId, "STREAM_START");
    }

    /**
     * Dispatches a command to a device via IDFS and waits for the ack.
     *
     * @param deviceId    the device to command
     * @param commandType the command (LOCK, UNLOCK, STREAM_START, etc.)
     * @return {@code true} if the device acknowledged successfully
     */
    private boolean dispatchCommand(String deviceId, String commandType) {
        String correlationId = UUID.randomUUID().toString();

        System.out.println("[DMS] Dispatching " + commandType + " to " + deviceId +
                " correlationId=" + correlationId);

        try {
            ServiceResponse response = httpClient.post(
                    idfsBaseUrl + "/command",
                    Map.of(
                            "device_id", deviceId,
                            "command_type", commandType,
                            "correlation_id", correlationId
                    )
            );

            if (response.isSuccess()) {
                Map result = JsonUtil.fromJson(response.body(), Map.class);
                boolean acknowledged = Boolean.TRUE.equals(result.get("acknowledged"));
                System.out.println("[DMS] Command " + commandType + " for " + deviceId +
                        " acknowledged=" + acknowledged);
                return acknowledged;
            }

            System.out.println("[DMS] Command " + commandType + " for " + deviceId +
                    " failed: HTTP " + response.statusCode() + " " + response.body());
            return false;

        } catch (IOException e) {
            System.err.println("[DMS] Failed to reach IDFS for command dispatch: " + e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // Firmware
    // =====================================================================

    @Override
    public String getLatestFirmwareVersion(DeviceType deviceType) {
        Objects.requireNonNull(deviceType, "deviceType");
        return storageGateway.getLatestFirmwareVersion(deviceType.name());
    }

    @Override
    public void pushFirmwareUpdate(String deviceId, String firmwareVersion)
            throws DeviceNotFoundException, DeviceOfflineException, IllegalArgumentException {
        ensureDeviceReachable(deviceId);
        requireNonBlank(firmwareVersion, "firmwareVersion");

        Device device = getDevice(deviceId);
        byte[] fw = storageGateway.loadFirmwareBinary(device.type().name(), firmwareVersion);
        if (fw.length == 0) {
            throw new IllegalArgumentException("Firmware binary is empty for version: " + firmwareVersion);
        }

        try {
            storageGateway.updateDevice(device.withFirmwareVersion(firmwareVersion));
        } catch (DeviceNotFoundException e) {
            throw new RuntimeException("Device disappeared during firmware push", e);
        }
    }

    // =====================================================================
    // Deregistration
    // =====================================================================

    @Override
    public void deregisterDevice(String deviceId, String ownerId)
            throws DeviceNotFoundException, IllegalArgumentException {
        requireNonBlank(ownerId, "ownerId");
        Device device = getDevice(deviceId);

        if (!device.ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("ownerId does not match device owner");
        }

        pendingTokens.remove(deviceId);
        lastHeartbeats.remove(deviceId);
        storageGateway.deleteDevice(deviceId);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private BootstrapRegistrationResult buildBootstrapResult(Device device, Instant issuedAt) {
        String latestVersion = storageGateway.getLatestFirmwareVersion(device.type().name());
        String fwVersion = (latestVersion == null || latestVersion.isBlank())
                ? device.firmwareVersion()
                : latestVersion;

        DeviceRegistrationInfo regInfo = new DeviceRegistrationInfo(
                device.deviceId(), device.type(), device.registeredAt()
        );

        FirmwareAssignment fwAssignment = new FirmwareAssignment(
                device.deviceId(),
                fwVersion,
                buildFirmwareUrl(device.type(), fwVersion),
                issuedAt
        );

        return new BootstrapRegistrationResult(regInfo, fwAssignment);
    }

    private String buildFirmwareUrl(DeviceType deviceType, String firmwareVersion) {
        return idfsBaseUrl + "/firmware/" +
                deviceType.name().toLowerCase() + "/" +
                firmwareVersion + ".bin";
    }

    private void ensureDeviceReachable(String deviceId)
            throws DeviceNotFoundException, DeviceOfflineException {
        Device device = getDevice(deviceId);
        if (device.status() == DeviceStatus.OFFLINE ||
                device.status() == DeviceStatus.UNRESPONSIVE ||
                device.status() == DeviceStatus.DEREGISTERED ||
                device.status() == DeviceStatus.PENDING_REGISTRATION) {
            throw new DeviceOfflineException(deviceId);
        }
    }

    private static ParsedQrPayload parseQrPayload(String qrPayload) {
        requireNonBlank(qrPayload, "qrPayload");
        String[] parts = qrPayload.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("qrPayload must be in the format 'deviceId:registrationToken'");
        }
        return new ParsedQrPayload(parts[0].trim(), parts[1].trim());
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static String defaultDisplayName(DeviceType deviceType, String deviceId) {
        return switch (deviceType) {
            case CAMERA -> "Camera " + deviceId;
            case SMART_LOCK -> "Smart Lock " + deviceId;
            case MOTION_SENSOR -> "Motion Sensor " + deviceId;
            case OTHER -> "Device " + deviceId;
        };
    }

    private record ParsedQrPayload(String deviceId, String registrationToken) {}
}