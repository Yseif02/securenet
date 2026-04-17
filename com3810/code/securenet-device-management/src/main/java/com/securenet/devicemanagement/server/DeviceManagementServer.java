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

/**
 * HTTP server for the Device Management Service.
 *
 * <p>Exposes device registration, heartbeat, command dispatch, and
 * firmware management endpoints. Called by the API Gateway (for
 * homeowner-initiated operations) and by IDFS (for device-initiated
 * bootstrap registration).
 */
public class DeviceManagementServer {

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

        httpServer.createContext("/dms/devices/register", this::handleRegisterDevice);
        httpServer.createContext("/dms/devices/accept-registration", this::handleAcceptRegistration);
        httpServer.createContext("/dms/devices/heartbeat", this::handleHeartbeat);
        httpServer.createContext("/dms/devices/status", this::handleUpdateStatus);
        httpServer.createContext("/dms/devices/lock", this::handleLock);
        httpServer.createContext("/dms/devices/unlock", this::handleUnlock);
        httpServer.createContext("/dms/devices/stream-start", this::handleStreamStart);
        httpServer.createContext("/dms/devices/firmware-version", this::handleFirmwareVersion);
        httpServer.createContext("/dms/devices/push-firmware", this::handlePushFirmware);
        httpServer.createContext("/dms/devices/deregister", this::handleDeregister);
        httpServer.createContext("/dms/devices/list", this::handleListDevices);
        httpServer.createContext("/dms/devices/get", this::handleGetDevice);
        httpServer.createContext("/health", ex -> writeJson(ex, 200, Map.of("status", "UP")));

        httpServer.start();
        System.out.println("[DeviceManagementService] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[DeviceManagementService] stopped");
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
            String ownerId = (String) body.get("ownerId");
            DeviceType deviceType = DeviceType.valueOf((String) body.get("deviceType"));
            String qrPayload = (String) body.get("qrPayload");

            Device device = service.registerDevice(ownerId, deviceType, qrPayload);
            writeJson(ex, 201, device);
        } catch (DeviceAlreadyRegisteredException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
            String deviceId = (String) body.get("deviceId");
            String registrationToken = (String) body.get("registrationToken");

            BootstrapRegistrationResult result =
                    service.acceptDeviceRegistration(deviceId, registrationToken);
            writeJson(ex, 200, result);
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
            service.recordHeartbeat((String) body.get("deviceId"));
            writeJson(ex, 204, Map.of("status", "ok"));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
            service.updateDeviceStatus(
                    (String) body.get("deviceId"),
                    DeviceStatus.valueOf((String) body.get("newStatus"))
            );
            writeJson(ex, 204, Map.of("status", "ok"));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
            boolean acked = service.sendLockCommand((String) body.get("deviceId"));
            writeJson(ex, 200, Map.of("acknowledged", acked));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
            boolean acked = service.sendUnlockCommand((String) body.get("deviceId"));
            writeJson(ex, 200, Map.of("acknowledged", acked));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
            service.sendStreamStartCommand(
                    (String) body.get("deviceId"),
                    (String) body.get("streamTargetUrl")
            );
            writeJson(ex, 202, Map.of("status", "ok"));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
                    if ("deviceType".equals(kv[0]) && kv.length == 2) {
                        deviceType = kv[1];
                    }
                }
            }
            String version = service.getLatestFirmwareVersion(DeviceType.valueOf(deviceType));
            if (version != null) {
                writeJson(ex, 200, Map.of("version", version));
            } else {
                writeJson(ex, 404, Map.of("error", "No firmware found"));
            }
        } catch (Exception e) {
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
            service.pushFirmwareUpdate(
                    (String) body.get("deviceId"),
                    (String) body.get("firmwareVersion")
            );
            writeJson(ex, 202, Map.of("status", "ok"));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DeviceOfflineException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
            service.deregisterDevice(
                    (String) body.get("deviceId"),
                    (String) body.get("ownerId")
            );
            writeJson(ex, 204, Map.of("status", "ok"));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Queries -----

    private void handleGetDevice(HttpExchange ex) throws IOException {
        try {
            String query = ex.getRequestURI().getQuery();
            String deviceId = extractParam(query, "deviceId");
            Device device = service.getDevice(deviceId);
            writeJson(ex, 200, device);
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleListDevices(HttpExchange ex) throws IOException {
        try {
            String query = ex.getRequestURI().getQuery();
            String ownerId = extractParam(query, "ownerId");
            List<Device> devices = service.listDevicesForOwner(ownerId);
            writeJson(ex, 200, devices);
        } catch (Exception e) {
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
