package com.securenet.devicemanagement.server;

import com.securenet.common.JsonUtil;
import com.securenet.devicemanagement.DeviceManagementService;
import com.securenet.model.Device;
import com.securenet.model.DeviceStatus;
import com.securenet.model.DeviceType;
import com.securenet.model.bootstrap.BootstrapRegistrationResult;
import com.securenet.model.exception.DeviceAlreadyRegisteredException;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.DeviceOfflineException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * HTTP server for the Device Management Service.
 *
 * <p>Exposes device registration, heartbeat, command dispatch, and
 * firmware management endpoints. Called by the API Gateway (for
 * homeowner-initiated operations) and by IDFS (for device-initiated
 * bootstrap registration).
 */
public class DeviceManagementServer {

    private static final Logger log = Logger.getLogger(DeviceManagementServer.class.getName());

    private final String host;
    private final int port;
    private final DeviceManagementService service;
    private HttpServer httpServer;

    /**
     * @param host    bind address
     * @param port    port to listen on
     * @param service the DMS business logic
     */
    public DeviceManagementServer(String host, int port, DeviceManagementService service) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.service = Objects.requireNonNull(service, "service");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));

        httpServer.createContext("/dms/devices/register",            this::handleRegisterDevice);
        httpServer.createContext("/dms/devices/accept-registration", this::handleAcceptRegistration);
        httpServer.createContext("/dms/devices/heartbeat",           this::handleHeartbeat);
        httpServer.createContext("/dms/devices/status",              this::handleUpdateStatus);
        httpServer.createContext("/dms/devices/lock",                this::handleLock);
        httpServer.createContext("/dms/devices/unlock",              this::handleUnlock);
        httpServer.createContext("/dms/devices/stream-start",        this::handleStreamStart);
        httpServer.createContext("/dms/devices/firmware-version",    this::handleFirmwareVersion);
        httpServer.createContext("/dms/devices/push-firmware",       this::handlePushFirmware);
        httpServer.createContext("/dms/devices/deregister",          this::handleDeregister);
        httpServer.createContext("/dms/devices/list",                this::handleListDevices);
        httpServer.createContext("/dms/devices/get",                 this::handleGetDevice);
        httpServer.createContext("/health", ex -> {
            log.fine("[DMS] Health check from " + ex.getRemoteAddress());
            writeJson(ex, 200, Map.of("status", "UP"));
        });

        httpServer.start();
        log.info("[DMS] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[DMS] stopped");
        }
    }

    // ----- Phase 0: Homeowner initiates registration -----

    private void handleRegisterDevice(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String ownerId    = (String) body.get("ownerId");
            String deviceType = (String) body.get("deviceType");
            String qrPayload  = (String) body.get("qrPayload");
            log.info("[DMS] POST /dms/devices/register ownerId=" + ownerId
                    + " deviceType=" + deviceType);
            Device device = service.registerDevice(ownerId, DeviceType.valueOf(deviceType), qrPayload);
            log.info("[DMS] Device registered: deviceId=" + device.deviceId()
                    + " status=" + device.status());
            writeJson(ex, 201, device);
        } catch (DeviceAlreadyRegisteredException e) {
            log.warning("[DMS] Register 409: " + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warning("[DMS] Register 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] Register 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Phase 3-4: Device bootstrap handshake (called by IDFS) -----

    private void handleAcceptRegistration(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId          = (String) body.get("deviceId");
            String registrationToken = (String) body.get("registrationToken");
            log.info("[DMS] POST /dms/devices/accept-registration deviceId=" + deviceId);
            BootstrapRegistrationResult result =
                    service.acceptDeviceRegistration(deviceId, registrationToken);
            log.info("[DMS] Bootstrap accepted: deviceId=" + deviceId);
            writeJson(ex, 200, result);
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] AcceptRegistration 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warning("[DMS] AcceptRegistration 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] AcceptRegistration 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Heartbeat -----

    private void handleHeartbeat(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId = (String) body.get("deviceId");
            log.fine("[DMS] POST /dms/devices/heartbeat deviceId=" + deviceId);
            service.recordHeartbeat(deviceId);
            ex.sendResponseHeaders(204, -1);
            ex.getResponseBody().close();
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] Heartbeat 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] Heartbeat 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Status update -----

    private void handleUpdateStatus(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId  = (String) body.get("deviceId");
            String newStatus = (String) body.get("newStatus");
            log.info("[DMS] POST /dms/devices/status deviceId=" + deviceId
                    + " newStatus=" + newStatus);
            service.updateDeviceStatus(deviceId, DeviceStatus.valueOf(newStatus));
            ex.sendResponseHeaders(204, -1);
            ex.getResponseBody().close();
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] UpdateStatus 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] UpdateStatus 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Lock/Unlock -----

    private void handleLock(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId = (String) body.get("deviceId");
            log.info("[DMS] POST /dms/devices/lock deviceId=" + deviceId);
            boolean acked = service.sendLockCommand(deviceId);
            log.info("[DMS] Lock result: deviceId=" + deviceId + " acknowledged=" + acked);
            writeJson(ex, 200, Map.of("acknowledged", acked));
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] Lock 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            log.warning("[DMS] Lock 409: device offline deviceId=" + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] Lock 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleUnlock(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId = (String) body.get("deviceId");
            log.info("[DMS] POST /dms/devices/unlock deviceId=" + deviceId);
            boolean acked = service.sendUnlockCommand(deviceId);
            log.info("[DMS] Unlock result: deviceId=" + deviceId + " acknowledged=" + acked);
            writeJson(ex, 200, Map.of("acknowledged", acked));
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] Unlock 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            log.warning("[DMS] Unlock 409: device offline deviceId=" + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] Unlock 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Stream start -----

    private void handleStreamStart(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId       = (String) body.get("deviceId");
            String streamTargetUrl = (String) body.get("streamTargetUrl");
            log.info("[DMS] POST /dms/devices/stream-start deviceId=" + deviceId
                    + " targetUrl=" + streamTargetUrl);
            service.sendStreamStartCommand(deviceId, streamTargetUrl);
            log.info("[DMS] Stream start dispatched: deviceId=" + deviceId);
            writeJson(ex, 202, Map.of("status", "ok"));
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] StreamStart 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            log.warning("[DMS] StreamStart 409: device offline " + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] StreamStart 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Firmware -----

    private void handleFirmwareVersion(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            String query = ex.getRequestURI().getQuery();
            String deviceType = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if ("deviceType".equals(kv[0]) && kv.length == 2) deviceType = kv[1];
                }
            }
            log.info("[DMS] GET /dms/devices/firmware-version deviceType=" + deviceType);
            String version = service.getLatestFirmwareVersion(DeviceType.valueOf(deviceType));
            if (version != null) {
                log.info("[DMS] Firmware version: deviceType=" + deviceType + " version=" + version);
                writeJson(ex, 200, Map.of("version", version));
            } else {
                log.warning("[DMS] Firmware version: not found for deviceType=" + deviceType);
                writeJson(ex, 404, Map.of("error", "No firmware found"));
            }
        } catch (Exception e) {
            log.severe("[DMS] FirmwareVersion 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handlePushFirmware(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId        = (String) body.get("deviceId");
            String firmwareVersion = (String) body.get("firmwareVersion");
            log.info("[DMS] POST /dms/devices/push-firmware deviceId=" + deviceId
                    + " version=" + firmwareVersion);
            service.pushFirmwareUpdate(deviceId, firmwareVersion);
            log.info("[DMS] Firmware pushed: deviceId=" + deviceId + " version=" + firmwareVersion);
            writeJson(ex, 202, Map.of("status", "ok"));
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] PushFirmware 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            log.warning("[DMS] PushFirmware 409: " + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warning("[DMS] PushFirmware 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] PushFirmware 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Deregistration -----

    private void handleDeregister(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String deviceId = (String) body.get("deviceId");
            String ownerId  = (String) body.get("ownerId");
            log.info("[DMS] POST /dms/devices/deregister deviceId=" + deviceId
                    + " ownerId=" + ownerId);
            service.deregisterDevice(deviceId, ownerId);
            log.info("[DMS] Device deregistered: deviceId=" + deviceId);
            ex.sendResponseHeaders(204, -1);
            ex.getResponseBody().close();
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] Deregister 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warning("[DMS] Deregister 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] Deregister 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Queries -----

    private void handleGetDevice(HttpExchange ex) throws IOException {
        try {
            String query    = ex.getRequestURI().getQuery();
            String deviceId = extractParam(query, "deviceId");
            log.info("[DMS] GET /dms/devices/get deviceId=" + deviceId);
            Device device = service.getDevice(deviceId);
            log.info("[DMS] getDevice: deviceId=" + deviceId + " status=" + device.status());
            writeJson(ex, 200, device);
        } catch (DeviceNotFoundException e) {
            log.warning("[DMS] GetDevice 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[DMS] GetDevice 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleListDevices(HttpExchange ex) throws IOException {
        try {
            String query   = ex.getRequestURI().getQuery();
            String ownerId = extractParam(query, "ownerId");
            log.info("[DMS] GET /dms/devices/list ownerId=" + ownerId);
            List<Device> devices = service.listDevicesForOwner(ownerId);
            log.info("[DMS] listDevices: found " + devices.size()
                    + " devices for ownerId=" + ownerId);
            writeJson(ex, 200, devices);
        } catch (Exception e) {
            log.severe("[DMS] ListDevices 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static <T> T readBody(HttpExchange ex, Class<T> clazz) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return JsonUtil.fromJson(new String(is.readAllBytes(), StandardCharsets.UTF_8), clazz);
        }
    }

    private static void writeJson(HttpExchange ex, int statusCode, Object body) throws IOException {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String extractParam(String query, String key) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv[0].equals(key) && kv.length == 2) return kv[1];
        }
        return null;
    }
}