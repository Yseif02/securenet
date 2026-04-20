package com.securenet.devicemanagement.impl;

import com.securenet.common.JsonUtil;
import com.securenet.common.LoadBalancer;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Distributed implementation of the Device Management Service.
 *
 * <p>Runs as its own process and accesses <strong>all</strong> persistent data
 * through {@link StorageGateway} (HTTP client to the remote Storage Service).
 * No in-memory state is kept — registration tokens, heartbeats, and device
 * records are all stored in PostgreSQL so that any DMS instance behind
 * the load balancer can serve any request.
 *
 * <p>Remote commands (lock, unlock, stream-start) are dispatched to
 * devices via the IDFS {@code /command} endpoint, which publishes to
 * the device's MQTT command topic and waits for an ack.
 */
public class DeviceManagementServiceImpl implements DeviceManagementService {

    private static final Logger log = Logger.getLogger(DeviceManagementServiceImpl.class.getName());

    private final StorageGateway storageGateway;
    //private final String idfsBaseUrl;
    private final LoadBalancer idfsLoadBalancer;

    private final ServiceClient httpClient;

    /**
     * @param storageGateway HTTP client pointing to the remote Storage Service
     * //@param idfsBaseUrl    base URL of the IDFS server, e.g. {@code "http://localhost:8080"}
     */
    public DeviceManagementServiceImpl(StorageGateway storageGateway, String idfsUrls) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.idfsLoadBalancer = new LoadBalancer("IDFS", Arrays.asList(idfsUrls.split(",")));
        this.idfsLoadBalancer.start();
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

        log.info("[DMS] registerDevice: ownerId=" + ownerId + " type=" + deviceType);

        ParsedQrPayload parsed = parseQrPayload(qrPayload);
        log.info("[DMS] QR parsed: deviceId=" + parsed.deviceId());

        Device existing = storageGateway.findDeviceById(parsed.deviceId()).orElse(null);
        if (existing != null && existing.status() != DeviceStatus.DEREGISTERED) {
            log.warning("[DMS] registerDevice failed: device already registered deviceId="
                    + parsed.deviceId() + " status=" + existing.status());
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
        storageGateway.saveRegistrationToken(parsed.deviceId(), parsed.registrationToken());

        log.info("[DMS] Device registered: deviceId=" + device.deviceId()
                + " status=" + device.status() + " owner=" + ownerId);
        return device;
    }

    @Override
    public BootstrapRegistrationResult acceptDeviceRegistration(String deviceId, String registrationToken)
            throws DeviceNotFoundException, IllegalArgumentException {
        requireNonBlank(deviceId, "deviceId");
        requireNonBlank(registrationToken, "registrationToken");

        log.info("[DMS] acceptDeviceRegistration: deviceId=" + deviceId);

        Device device = storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> {
                    log.warning("[DMS] acceptDeviceRegistration failed: device not found deviceId=" + deviceId);
                    return new DeviceNotFoundException(deviceId);
                });

        String expectedToken = storageGateway.findRegistrationToken(deviceId).orElse(null);
        if (expectedToken == null) {
            log.warning("[DMS] acceptDeviceRegistration failed: no registration token for deviceId=" + deviceId);
            throw new DeviceNotFoundException(deviceId);
        }
        if (!expectedToken.equals(registrationToken)) {
            log.warning("[DMS] acceptDeviceRegistration failed: invalid token for deviceId=" + deviceId);
            throw new IllegalArgumentException("Invalid registration token for device: " + deviceId);
        }

        Instant now = Instant.now();

        if (device.status() == DeviceStatus.PENDING_REGISTRATION) {
            try {
                storageGateway.updateDevice(device.withStatus(DeviceStatus.ONLINE));
                log.info("[DMS] Device transitioned to ONLINE: deviceId=" + deviceId);
            } catch (DeviceNotFoundException e) {
                throw new RuntimeException("Device disappeared during registration", e);
            }
            device = storageGateway.findDeviceById(deviceId)
                    .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        }

        storageGateway.saveDeviceHeartbeat(deviceId, now);
        log.info("[DMS] Bootstrap complete: deviceId=" + deviceId
                + " status=" + device.status() + " firmware=" + device.firmwareVersion());

        return buildBootstrapResult(device, now);
    }

    // =====================================================================
    // Registry queries
    // =====================================================================

    @Override
    public Device getDevice(String deviceId) throws DeviceNotFoundException {
        requireNonBlank(deviceId, "deviceId");
        return storageGateway.findDeviceById(deviceId)
                .orElseThrow(() -> {
                    log.warning("[DMS] getDevice: not found deviceId=" + deviceId);
                    return new DeviceNotFoundException(deviceId);
                });
    }

    @Override
    public List<Device> listDevicesForOwner(String ownerId) {
        requireNonBlank(ownerId, "ownerId");
        log.info("[DMS] listDevicesForOwner: ownerId=" + ownerId);
        List<Device> devices = storageGateway.findDevicesByOwner(ownerId);
        log.info("[DMS] listDevicesForOwner: found " + devices.size()
                + " devices for ownerId=" + ownerId);
        return devices;
    }

    // =====================================================================
    // Heartbeat and status
    // =====================================================================

    @Override
    public void recordHeartbeat(String deviceId) throws DeviceNotFoundException {
        Device device = getDevice(deviceId);
        storageGateway.saveDeviceHeartbeat(deviceId, Instant.now());
        log.fine("[DMS] Heartbeat recorded: deviceId=" + deviceId + " status=" + device.status());

        if (device.status() == DeviceStatus.OFFLINE ||
                device.status() == DeviceStatus.UNRESPONSIVE ||
                device.status() == DeviceStatus.PENDING_REGISTRATION) {
            try {
                storageGateway.updateDevice(device.withStatus(DeviceStatus.ONLINE));
                log.info("[DMS] Device recovered to ONLINE via heartbeat: deviceId=" + deviceId
                        + " previousStatus=" + device.status());
            } catch (DeviceNotFoundException e) {
                throw new RuntimeException("Device disappeared during heartbeat", e);
            }
        }
    }

    @Override
    public void markDeviceUnresponsive(String deviceId) throws DeviceNotFoundException {
        Device device = getDevice(deviceId);
        log.warning("[DMS] Marking device UNRESPONSIVE: deviceId=" + deviceId
                + " previousStatus=" + device.status());
        storageGateway.updateDevice(device.withStatus(DeviceStatus.UNRESPONSIVE));
    }

    @Override
    public void updateDeviceStatus(String deviceId, DeviceStatus newStatus)
            throws DeviceNotFoundException {
        Objects.requireNonNull(newStatus, "newStatus");
        Device device = getDevice(deviceId);
        log.info("[DMS] updateDeviceStatus: deviceId=" + deviceId
                + " " + device.status() + " -> " + newStatus);
        storageGateway.updateDevice(device.withStatus(newStatus));
    }

    // =====================================================================
    // Remote commands — dispatched via IDFS /command → MQTT → device
    // =====================================================================

    @Override
    public boolean sendLockCommand(String deviceId)
            throws DeviceNotFoundException, DeviceOfflineException {
        ensureDeviceReachable(deviceId);
        log.info("[DMS] sendLockCommand: deviceId=" + deviceId);
        return dispatchCommand(deviceId, "LOCK");
    }

    @Override
    public boolean sendUnlockCommand(String deviceId)
            throws DeviceNotFoundException, DeviceOfflineException {
        ensureDeviceReachable(deviceId);
        log.info("[DMS] sendUnlockCommand: deviceId=" + deviceId);
        return dispatchCommand(deviceId, "UNLOCK");
    }

    @Override
    public void sendStreamStartCommand(String deviceId, String streamTargetUrl)
            throws DeviceNotFoundException, DeviceOfflineException {
        ensureDeviceReachable(deviceId);
        requireNonBlank(streamTargetUrl, "streamTargetUrl");
        log.info("[DMS] sendStreamStartCommand: deviceId=" + deviceId
                + " targetUrl=" + streamTargetUrl);
        dispatchCommand(deviceId, "STREAM_START");
    }

    private boolean dispatchCommand(String deviceId, String commandType) {
        String correlationId = UUID.randomUUID().toString();
        String idfsUrl = idfsLoadBalancer.nextHealthyUrl();
        log.info("[DMS] Dispatching " + commandType + " to " + deviceId
                + " via " + idfsUrl + " correlationId=" + correlationId);

        try {
            ServiceResponse response = httpClient.post(
                    idfsUrl + "/command",
                    java.util.Map.of(
                            "device_id", deviceId,
                            "command_type", commandType,
                            "correlation_id", correlationId
                    )
            );

            if (response.isSuccess()) {
                java.util.Map result = JsonUtil.fromJson(response.body(), java.util.Map.class);
                boolean acknowledged = Boolean.TRUE.equals(result.get("acknowledged"));
                log.info("[DMS] Command " + commandType + " for " + deviceId
                        + " acknowledged=" + acknowledged);
                return acknowledged;
            }

            log.warning("[DMS] Command " + commandType + " for " + deviceId
                    + " failed: HTTP " + response.statusCode() + " body=" + response.body());
            return false;

        } catch (IOException e) {
            log.severe("[DMS] Failed to reach IDFS for command dispatch: deviceId=" + deviceId
                    + " command=" + commandType + " error=" + e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // Firmware
    // =====================================================================

    @Override
    public String getLatestFirmwareVersion(DeviceType deviceType) {
        Objects.requireNonNull(deviceType, "deviceType");
        log.info("[DMS] getLatestFirmwareVersion: deviceType=" + deviceType);
        String version = storageGateway.getLatestFirmwareVersion(deviceType.name());
        log.info("[DMS] latestFirmwareVersion: deviceType=" + deviceType + " version=" + version);
        return version;
    }

    @Override
    public void pushFirmwareUpdate(String deviceId, String firmwareVersion)
            throws DeviceNotFoundException, DeviceOfflineException, IllegalArgumentException {
        ensureDeviceReachable(deviceId);
        requireNonBlank(firmwareVersion, "firmwareVersion");

        log.info("[DMS] pushFirmwareUpdate: deviceId=" + deviceId + " version=" + firmwareVersion);

        Device device = getDevice(deviceId);
        byte[] fw = storageGateway.loadFirmwareBinary(device.type().name(), firmwareVersion);
        if (fw.length == 0) {
            log.warning("[DMS] pushFirmwareUpdate failed: empty binary for version=" + firmwareVersion);
            throw new IllegalArgumentException("Firmware binary is empty for version: " + firmwareVersion);
        }

        try {
            storageGateway.updateDevice(device.withFirmwareVersion(firmwareVersion));
            log.info("[DMS] Firmware updated: deviceId=" + deviceId + " version=" + firmwareVersion);
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
        log.info("[DMS] deregisterDevice: deviceId=" + deviceId + " ownerId=" + ownerId);

        Device device = getDevice(deviceId);

        if (!device.ownerId().equals(ownerId)) {
            log.warning("[DMS] deregisterDevice failed: ownerId mismatch deviceId=" + deviceId);
            throw new IllegalArgumentException("ownerId does not match device owner");
        }

        storageGateway.deleteRegistrationToken(deviceId);
        storageGateway.deleteDevice(deviceId);
        log.info("[DMS] Device deregistered: deviceId=" + deviceId);
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
        return idfsLoadBalancer.nextHealthyUrl() + "/firmware/" +
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
            log.warning("[DMS] ensureDeviceReachable: device not reachable deviceId=" + deviceId
                    + " status=" + device.status());
            throw new DeviceOfflineException(deviceId);
        }
    }

    private static ParsedQrPayload parseQrPayload(String qrPayload) {
        requireNonBlank(qrPayload, "qrPayload");
        String[] parts = qrPayload.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "qrPayload must be in the format 'deviceId:registrationToken'");
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
            case CAMERA       -> "Camera " + deviceId;
            case SMART_LOCK   -> "Smart Lock " + deviceId;
            case MOTION_SENSOR -> "Motion Sensor " + deviceId;
            case OTHER        -> "Device " + deviceId;
        };
    }

    private record ParsedQrPayload(String deviceId, String registrationToken) {}
}