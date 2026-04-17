package com.securenet.storage.server;

import com.securenet.common.JsonUtil;
import com.securenet.model.*;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.VideoNotFoundException;
import com.securenet.storage.StorageService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * HTTP server that exposes the {@link StorageService} API over REST/JSON.
 *
 * <p>Every other SecureNet service (UMS, DMS, EPS, etc.) accesses persistent
 * data through this server via the {@link com.securenet.storage.StorageGateway}
 * HTTP client.
 *
 * <p>This server is the single persistence endpoint in the SecureNet
 * architecture. In a production deployment it would front a PostgreSQL
 * cluster with replication; here it wraps the in-memory implementation.
 *
 * @see com.securenet.storage.StorageGateway
 */
public class StorageServiceServer {

    private final String host;
    private final int port;
    private final StorageService storageService;
    private HttpServer httpServer;

    /**
     * @param host           bind address (e.g. "0.0.0.0" or "localhost")
     * @param port           port to listen on
     * @param storageService the backing storage implementation
     */
    public StorageServiceServer(String host, int port, StorageService storageService) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.storageService = Objects.requireNonNull(storageService, "storageService");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));

        // ----- User endpoints -----
        httpServer.createContext("/storage/users", this::handleUsers);
        httpServer.createContext("/storage/passwords", this::handlePasswords);
        httpServer.createContext("/storage/auth-tokens", this::handleAuthTokens);

        // ----- Device endpoints -----
        httpServer.createContext("/storage/devices", this::handleDevices);

        // ----- Event endpoints -----
        httpServer.createContext("/storage/events", this::handleEvents);

        // ----- Video endpoints -----
        httpServer.createContext("/storage/video-clips", this::handleVideoClips);
        httpServer.createContext("/storage/video-bytes", this::handleVideoBytes);

        // ----- Firmware endpoints -----
        httpServer.createContext("/storage/firmware", this::handleFirmware);

        // ----- Push token endpoints -----
        httpServer.createContext("/storage/push-tokens", this::handlePushTokens);

        // ----- Health check -----
        httpServer.createContext("/health", ex -> writeJson(ex, 200, Map.of("status", "UP")));

        httpServer.createContext("/storage/registration-tokens", this::handleRegistrationTokens);
        httpServer.createContext("/storage/device-heartbeats", this::handleDeviceHeartbeats);
        httpServer.createContext("/storage/eps-dedup", this::handleEpsDedup);
        httpServer.createContext("/storage/eps-motion-cooldown", this::handleEpsMotionCooldown);
        httpServer.createContext("/storage/eps-lamport-clock", this::handleEpsLamportClock);
        httpServer.createContext("/storage/recording-sessions", this::handleRecordingSessions);
        httpServer.createContext("/storage/notification-outbox", this::handleNotificationOutbox);

        httpServer.start();
        System.out.println("[StorageService] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[StorageService] stopped");
        }
    }

    // =====================================================================
    // User handlers
    // =====================================================================

    private void handleUsers(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String[] segments = path.split("/");
            // /storage/users                    -> segments: ["", "storage", "users"]
            // /storage/users/{id}               -> segments: ["", "storage", "users", "{id}"]
            // /storage/users/email/{email}       -> segments: ["", "storage", "users", "email", "{email}"]

            if ("POST".equals(method) && segments.length == 3) {
                User user = readBody(ex, User.class);
                storageService.saveUser(user);
                writeJson(ex, 201, user);
                return;
            }

            if ("PUT".equals(method) && segments.length == 3) {
                User user = readBody(ex, User.class);
                storageService.updateUser(user);
                writeJson(ex, 200, user);
                return;
            }

            if ("GET".equals(method)) {
                if (segments.length == 5 && "email".equals(segments[3])) {
                    String email = URLDecoder.decode(segments[4], StandardCharsets.UTF_8);
                    Optional<User> user = storageService.findUserByEmail(email);
                    if (user.isPresent()) {
                        writeJson(ex, 200, user.get());
                    } else {
                        writeJson(ex, 404, Map.of("error", "User not found"));
                    }
                    return;
                }
                if (segments.length == 4) {
                    String userId = segments[3];
                    Optional<User> user = storageService.findUserById(userId);
                    if (user.isPresent()) {
                        writeJson(ex, 200, user.get());
                    } else {
                        writeJson(ex, 404, Map.of("error", "User not found"));
                    }
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Password handlers
    // =====================================================================

    private void handlePasswords(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String[] segments = path.split("/");
            // /storage/passwords/{userId}  -> segments: ["", "storage", "passwords", "{userId}"]

            if (segments.length < 4) {
                writeJson(ex, 400, Map.of("error", "userId required"));
                return;
            }

            String userId = segments[3];

            if ("POST".equals(method)) {
                Map body = readBody(ex, Map.class);
                String hash = (String) body.get("hash");
                storageService.savePasswordHash(userId, hash);
                writeJson(ex, 201, Map.of("status", "ok"));
                return;
            }

            if ("GET".equals(method)) {
                Optional<String> hash = storageService.findPasswordHashByUserId(userId);
                if (hash.isPresent()) {
                    writeJson(ex, 200, Map.of("hash", hash.get()));
                } else {
                    writeJson(ex, 404, Map.of("error", "Password not found"));
                }
                return;
            }

            writeJson(ex, 405, Map.of("error", "Method not allowed"));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Auth token handlers
    // =====================================================================

    private void handleAuthTokens(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String[] segments = path.split("/");
            // /storage/auth-tokens                       POST save
            // /storage/auth-tokens/{value}               GET  find
            // /storage/auth-tokens/{value}/revoke        POST revoke
            // /storage/auth-tokens/{value}/revoked       GET  isRevoked

            if ("POST".equals(method) && segments.length == 3) {
                AuthToken token = readBody(ex, AuthToken.class);
                storageService.saveAuthToken(token);
                writeJson(ex, 201, token);
                return;
            }

            if (segments.length >= 4) {
                String tokenValue = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);

                if ("GET".equals(method) && segments.length == 4) {
                    Optional<AuthToken> token = storageService.findAuthToken(tokenValue);
                    if (token.isPresent()) {
                        writeJson(ex, 200, token.get());
                    } else {
                        writeJson(ex, 404, Map.of("error", "Token not found"));
                    }
                    return;
                }

                if ("POST".equals(method) && segments.length == 5 && "revoke".equals(segments[4])) {
                    storageService.revokeAuthToken(tokenValue);
                    writeJson(ex, 200, Map.of("status", "ok"));
                    return;
                }

                if ("GET".equals(method) && segments.length == 5 && "revoked".equals(segments[4])) {
                    boolean revoked = storageService.isTokenRevoked(tokenValue);
                    writeJson(ex, 200, Map.of("revoked", revoked));
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Device handlers
    // =====================================================================

    private void handleDevices(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String[] segments = path.split("/");
            // /storage/devices                       POST save
            // /storage/devices                       PUT  update
            // /storage/devices/{id}                  GET  findById
            // /storage/devices/{id}                  DELETE delete
            // /storage/devices/owner/{ownerId}       GET  findByOwner

            if ("POST".equals(method) && segments.length == 3) {
                Device device = readBody(ex, Device.class);
                storageService.saveDevice(device);
                writeJson(ex, 201, device);
                return;
            }

            if ("PUT".equals(method) && segments.length == 3) {
                Device device = readBody(ex, Device.class);
                storageService.updateDevice(device);
                writeJson(ex, 200, device);
                return;
            }

            if ("GET".equals(method)) {
                if (segments.length == 5 && "owner".equals(segments[3])) {
                    String ownerId = segments[4];
                    List<Device> devices = storageService.findDevicesByOwner(ownerId);
                    writeJson(ex, 200, devices);
                    return;
                }
                if (segments.length == 4) {
                    String deviceId = segments[3];
                    Optional<Device> device = storageService.findDeviceById(deviceId);
                    if (device.isPresent()) {
                        writeJson(ex, 200, device.get());
                    } else {
                        writeJson(ex, 404, Map.of("error", "Device not found"));
                    }
                    return;
                }
            }

            if ("DELETE".equals(method) && segments.length == 4) {
                String deviceId = segments[3];
                storageService.deleteDevice(deviceId);
                writeJson(ex, 204, Map.of("status", "ok"));
                return;
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Event handlers
    // =====================================================================

    private void handleEvents(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery();
            String[] segments = path.split("/");
            // /storage/events                               POST save
            // /storage/events/{id}                          GET  findById
            // /storage/events/device/{deviceId}?from=&to=&max=  GET findByDevice
            // /storage/events/owner/{ownerId}/type/{type}?...   GET findByOwnerAndType

            if ("POST".equals(method) && segments.length == 3) {
                SecurityEvent event = readBody(ex, SecurityEvent.class);
                storageService.saveEvent(event);
                writeJson(ex, 201, event);
                return;
            }

            if ("GET".equals(method)) {
                Map<String, String> params = parseQuery(query);

                if (segments.length == 5 && "device".equals(segments[3])) {
                    String deviceId = segments[4];
                    Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
                    Instant to = Instant.parse(params.getOrDefault("to", "2099-12-31T23:59:59Z"));
                    int max = Integer.parseInt(params.getOrDefault("max", "500"));
                    List<SecurityEvent> events = storageService.findEventsByDevice(deviceId, from, to, max);
                    writeJson(ex, 200, events);
                    return;
                }

                if (segments.length == 7 && "owner".equals(segments[3]) && "type".equals(segments[5])) {
                    String ownerId = segments[4];
                    String eventType = segments[6];
                    Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
                    Instant to = Instant.parse(params.getOrDefault("to", "2099-12-31T23:59:59Z"));
                    int max = Integer.parseInt(params.getOrDefault("max", "500"));
                    List<SecurityEvent> events = storageService.findEventsByOwnerAndType(ownerId, eventType, from, to, max);
                    writeJson(ex, 200, events);
                    return;
                }

                if (segments.length == 4) {
                    Optional<SecurityEvent> event = storageService.findEventById(segments[3]);
                    if (event.isPresent()) {
                        writeJson(ex, 200, event.get());
                    } else {
                        writeJson(ex, 404, Map.of("error", "Event not found"));
                    }
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Video clip handlers
    // =====================================================================

    private void handleVideoClips(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery();
            String[] segments = path.split("/");

            if ("POST".equals(method) && segments.length == 3) {
                // For video clips, body is JSON with clip metadata + base64 bytes
                Map body = readBody(ex, Map.class);
                VideoClip clip = JsonUtil.fromJson(JsonUtil.toJson(body.get("clip")), VideoClip.class);
                String b64 = (String) body.get("bytes");
                byte[] rawBytes = Base64.getDecoder().decode(b64);
                storageService.saveVideoClip(clip, rawBytes);
                writeJson(ex, 201, clip);
                return;
            }

            if ("GET".equals(method)) {
                Map<String, String> params = parseQuery(query);

                if (segments.length == 5 && "device".equals(segments[3])) {
                    String deviceId = segments[4];
                    Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
                    Instant to = Instant.parse(params.getOrDefault("to", "2099-12-31T23:59:59Z"));
                    List<VideoClip> clips = storageService.findClipsByDevice(deviceId, from, to);
                    writeJson(ex, 200, clips);
                    return;
                }

                if (segments.length == 4) {
                    Optional<VideoClip> clip = storageService.findClipById(segments[3]);
                    if (clip.isPresent()) {
                        writeJson(ex, 200, clip.get());
                    } else {
                        writeJson(ex, 404, Map.of("error", "Clip not found"));
                    }
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleVideoBytes(HttpExchange ex) throws IOException {
        try {
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("GET".equals(ex.getRequestMethod()) && segments.length == 4) {
                String storageKey = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);
                byte[] data = storageService.loadVideoBytes(storageKey);
                String b64 = Base64.getEncoder().encodeToString(data);
                writeJson(ex, 200, Map.of("bytes", b64));
                return;
            }
            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (VideoNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Firmware handlers
    // =====================================================================

    private void handleFirmware(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String[] segments = path.split("/");
            // /storage/firmware/{type}/latest           GET  getLatestVersion
            // /storage/firmware/{type}/{version}        GET  loadBinary
            // /storage/firmware/{type}/{version}        POST saveBinary

            if (segments.length < 5) {
                writeJson(ex, 400, Map.of("error", "Bad request"));
                return;
            }

            String deviceTypeKey = segments[3];

            if ("latest".equals(segments[4]) && "GET".equals(method)) {
                String version = storageService.getLatestFirmwareVersion(deviceTypeKey);
                if (version != null) {
                    writeJson(ex, 200, Map.of("version", version));
                } else {
                    writeJson(ex, 404, Map.of("error", "No firmware for type: " + deviceTypeKey));
                }
                return;
            }

            String firmwareVersion = segments[4];

            if ("GET".equals(method)) {
                byte[] binary = storageService.loadFirmwareBinary(deviceTypeKey, firmwareVersion);
                String b64 = Base64.getEncoder().encodeToString(binary);
                writeJson(ex, 200, Map.of("bytes", b64));
                return;
            }

            if ("POST".equals(method)) {
                Map body = readBody(ex, Map.class);
                String b64 = (String) body.get("bytes");
                byte[] binary = Base64.getDecoder().decode(b64);
                storageService.saveFirmwareBinary(deviceTypeKey, firmwareVersion, binary);
                writeJson(ex, 201, Map.of("status", "ok"));
                return;
            }

            writeJson(ex, 405, Map.of("error", "Method not allowed"));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Push token handlers
    // =====================================================================

    private void handlePushTokens(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String[] segments = path.split("/");
            // /storage/push-tokens                          POST save
            // /storage/push-tokens/{token}                  DELETE remove
            // /storage/push-tokens/user/{userId}            GET  findByUser

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.savePushToken(
                        (String) body.get("userId"),
                        (String) body.get("token"),
                        (String) body.get("platform")
                );
                writeJson(ex, 201, Map.of("status", "ok"));
                return;
            }

            if ("DELETE".equals(method) && segments.length == 4) {
                String token = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);
                storageService.deletePushToken(token);
                writeJson(ex, 200, Map.of("status", "ok"));
                return;
            }

            if ("GET".equals(method) && segments.length == 5 && "user".equals(segments[3])) {
                List<String> tokens = storageService.findPushTokensByUser(segments[4]);
                writeJson(ex, 200, tokens);
                return;
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }


    private void handleRegistrationTokens(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.saveRegistrationToken(
                        (String) body.get("deviceId"), (String) body.get("registrationToken"));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 4) {
                Optional<String> token = storageService.findRegistrationToken(segments[3]);
                if (token.isPresent()) writeJson(ex, 200, Map.of("registrationToken", token.get()));
                else writeJson(ex, 404, Map.of("error", "No registration token found"));
            } else if ("DELETE".equals(method) && segments.length == 4) {
                storageService.deleteRegistrationToken(segments[3]);
                writeJson(ex, 200, Map.of("status", "ok"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) { writeJson(ex, 500, Map.of("error", e.getMessage())); }
    }

    private void handleDeviceHeartbeats(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.saveDeviceHeartbeat(
                        (String) body.get("deviceId"), Instant.parse((String) body.get("heartbeatAt")));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 4) {
                Optional<Instant> hb = storageService.findDeviceHeartbeat(segments[3]);
                if (hb.isPresent()) writeJson(ex, 200, Map.of("heartbeatAt", hb.get().toString()));
                else writeJson(ex, 404, Map.of("error", "No heartbeat found"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) { writeJson(ex, 500, Map.of("error", e.getMessage())); }
    }

    private void handleEpsDedup(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.saveDeduplicationEntry((String) body.get("dedupKey"),
                        (String) body.get("eventId"), Instant.parse((String) body.get("recordedAt")));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("POST".equals(method) && segments.length == 4 && "cleanup".equals(segments[3])) {
                Map body = readBody(ex, Map.class);
                int deleted = storageService.deleteExpiredDeduplicationEntries(
                        Instant.parse((String) body.get("olderThan")));
                writeJson(ex, 200, Map.of("deleted", deleted));
            } else if ("GET".equals(method) && segments.length == 4) {
                String key = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);
                Optional<Map<String, String>> entry = storageService.findDeduplicationEntry(key);
                if (entry.isPresent()) writeJson(ex, 200, entry.get());
                else writeJson(ex, 404, Map.of("error", "Dedup entry not found"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) { writeJson(ex, 500, Map.of("error", e.getMessage())); }
    }

    private void handleEpsMotionCooldown(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.saveMotionCooldown(
                        (String) body.get("deviceId"), Instant.parse((String) body.get("alertAt")));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 4) {
                Optional<Instant> cd = storageService.findMotionCooldown(segments[3]);
                if (cd.isPresent()) writeJson(ex, 200, Map.of("alertAt", cd.get().toString()));
                else writeJson(ex, 404, Map.of("error", "No motion cooldown found"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) { writeJson(ex, 500, Map.of("error", e.getMessage())); }
    }

    private void handleEpsLamportClock(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.saveLamportClock(
                        (String) body.get("nodeId"), ((Number) body.get("value")).longValue());
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 4) {
                long value = storageService.findLamportClock(segments[3]);
                writeJson(ex, 200, Map.of("value", value));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) { writeJson(ex, 500, Map.of("error", e.getMessage())); }
    }

    private void handleRecordingSessions(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.saveRecordingSession((String) body.get("sessionId"),
                        (String) body.get("deviceId"), (String) body.get("ownerId"),
                        Instant.parse((String) body.get("startedAt")));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 5 && "device".equals(segments[3])) {
                Optional<String> sid = storageService.findActiveSessionForDevice(segments[4]);
                if (sid.isPresent()) writeJson(ex, 200, Map.of("sessionId", sid.get()));
                else writeJson(ex, 404, Map.of("error", "No active session"));
            } else if ("GET".equals(method) && segments.length == 4) {
                Optional<Map<String, String>> session = storageService.findRecordingSession(segments[3]);
                if (session.isPresent()) writeJson(ex, 200, session.get());
                else writeJson(ex, 404, Map.of("error", "Session not found"));
            } else if ("DELETE".equals(method) && segments.length == 4) {
                storageService.deleteRecordingSession(segments[3]);
                writeJson(ex, 200, Map.of("status", "ok"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) { writeJson(ex, 500, Map.of("error", e.getMessage())); }
    }

    private void handleNotificationOutbox(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.saveNotificationOutbox((String) body.get("notificationId"),
                        (String) body.get("token"), (String) body.get("payload"),
                        ((Number) body.get("attempts")).intValue());
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("POST".equals(method) && segments.length == 5 && "attempts".equals(segments[4])) {
                Map body = readBody(ex, Map.class);
                storageService.updateNotificationAttempts(segments[3],
                        ((Number) body.get("attempts")).intValue());
                writeJson(ex, 200, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 3) {
                Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
                int max = Integer.parseInt(params.getOrDefault("max", "50"));
                writeJson(ex, 200, storageService.findPendingNotifications(max));
            } else if ("DELETE".equals(method) && segments.length == 4) {
                storageService.deleteNotificationOutbox(segments[3]);
                writeJson(ex, 200, Map.of("status", "ok"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) { writeJson(ex, 500, Map.of("error", e.getMessage())); }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static <T> T readBody(HttpExchange ex, Class<T> clazz) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return JsonUtil.fromJson(body, clazz);
        }
    }

    private static void writeJson(HttpExchange ex, int statusCode, Object body) throws IOException {
        String json = JsonUtil.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Map.of();
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }
}
