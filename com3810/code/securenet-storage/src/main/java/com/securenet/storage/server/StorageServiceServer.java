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
import java.util.logging.Logger;

/**
 * HTTP server that exposes the {@link StorageService} API over REST/JSON.
 *
 * <p>Every other SecureNet service (UMS, DMS, EPS, etc.) accesses persistent
 * data through this server via the {@link com.securenet.storage.StorageGateway}
 * HTTP client.
 */
public class StorageServiceServer {

    private static final Logger log = Logger.getLogger(StorageServiceServer.class.getName());

    private final String host;
    private final int port;
    private final StorageService storageService;
    private HttpServer httpServer;

    public StorageServiceServer(String host, int port, StorageService storageService) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.storageService = Objects.requireNonNull(storageService, "storageService");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));

        httpServer.createContext("/storage/users",                this::handleUsers);
        httpServer.createContext("/storage/passwords",            this::handlePasswords);
        httpServer.createContext("/storage/auth-tokens",          this::handleAuthTokens);
        httpServer.createContext("/storage/devices",              this::handleDevices);
        httpServer.createContext("/storage/events",               this::handleEvents);
        httpServer.createContext("/storage/video-clips",          this::handleVideoClips);
        httpServer.createContext("/storage/video-bytes",          this::handleVideoBytes);
        httpServer.createContext("/storage/firmware",             this::handleFirmware);
        httpServer.createContext("/storage/push-tokens",          this::handlePushTokens);
        httpServer.createContext("/storage/registration-tokens",  this::handleRegistrationTokens);
        httpServer.createContext("/storage/device-heartbeats",    this::handleDeviceHeartbeats);
        httpServer.createContext("/storage/eps-dedup",            this::handleEpsDedup);
        httpServer.createContext("/storage/eps-motion-cooldown",  this::handleEpsMotionCooldown);
        httpServer.createContext("/storage/eps-lamport-clock",    this::handleEpsLamportClock);
        httpServer.createContext("/storage/recording-sessions",   this::handleRecordingSessions);
        httpServer.createContext("/storage/notification-outbox",  this::handleNotificationOutbox);
        httpServer.createContext("/storage/pending-commands", this::handlePendingCommands);
        httpServer.createContext("/health", ex -> {
            log.fine("[Storage] Health check from " + ex.getRemoteAddress());
            writeJson(ex, 200, Map.of("status", "UP"));
        });

        httpServer.start();
        log.info("[Storage] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[Storage] stopped");
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

            if ("POST".equals(method) && segments.length == 3) {
                User user = readBody(ex, User.class);
                log.info("[Storage] saveUser: userId=" + user.userId() + " email=" + user.email());
                storageService.saveUser(user);
                writeJson(ex, 201, user);
                return;
            }

            if ("PUT".equals(method) && segments.length == 3) {
                User user = readBody(ex, User.class);
                log.info("[Storage] updateUser: userId=" + user.userId());
                storageService.updateUser(user);
                writeJson(ex, 200, user);
                return;
            }

            if ("GET".equals(method)) {
                if (segments.length == 5 && "email".equals(segments[3])) {
                    String email = URLDecoder.decode(segments[4], StandardCharsets.UTF_8);
                    log.info("[Storage] findUserByEmail: email=" + email);
                    Optional<User> user = storageService.findUserByEmail(email);
                    if (user.isPresent()) {
                        writeJson(ex, 200, user.get());
                    } else {
                        log.fine("[Storage] findUserByEmail: not found email=" + email);
                        writeJson(ex, 404, Map.of("error", "User not found"));
                    }
                    return;
                }
                if (segments.length == 4) {
                    String userId = segments[3];
                    log.info("[Storage] findUserById: userId=" + userId);
                    Optional<User> user = storageService.findUserById(userId);
                    if (user.isPresent()) {
                        writeJson(ex, 200, user.get());
                    } else {
                        log.fine("[Storage] findUserById: not found userId=" + userId);
                        writeJson(ex, 404, Map.of("error", "User not found"));
                    }
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (IllegalArgumentException e) {
            log.warning("[Storage] saveUser conflict: " + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[Storage] handleUsers 500: " + e.getMessage());
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

            if (segments.length < 4) {
                writeJson(ex, 400, Map.of("error", "userId required"));
                return;
            }

            String userId = segments[3];

            if ("POST".equals(method)) {
                Map body = readBody(ex, Map.class);
                log.info("[Storage] savePasswordHash: userId=" + userId);
                storageService.savePasswordHash(userId, (String) body.get("hash"));
                writeJson(ex, 201, Map.of("status", "ok"));
                return;
            }

            if ("GET".equals(method)) {
                log.info("[Storage] findPasswordHash: userId=" + userId);
                Optional<String> hash = storageService.findPasswordHashByUserId(userId);
                if (hash.isPresent()) {
                    writeJson(ex, 200, Map.of("hash", hash.get()));
                } else {
                    log.fine("[Storage] findPasswordHash: not found userId=" + userId);
                    writeJson(ex, 404, Map.of("error", "Password not found"));
                }
                return;
            }

            writeJson(ex, 405, Map.of("error", "Method not allowed"));
        } catch (Exception e) {
            log.severe("[Storage] handlePasswords 500: " + e.getMessage());
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

            if ("POST".equals(method) && segments.length == 3) {
                AuthToken token = readBody(ex, AuthToken.class);
                log.info("[Storage] saveAuthToken: tokenId=" + token.tokenValue()
                        + " userId=" + token.userId());
                storageService.saveAuthToken(token);
                writeJson(ex, 201, token);
                return;
            }

            if (segments.length >= 4) {
                String tokenValue = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);

                if ("GET".equals(method) && segments.length == 4) {
                    log.info("[Storage] findAuthToken: tokenId=" + tokenValue);
                    Optional<AuthToken> token = storageService.findAuthToken(tokenValue);
                    if (token.isPresent()) {
                        writeJson(ex, 200, token.get());
                    } else {
                        log.fine("[Storage] findAuthToken: not found tokenId=" + tokenValue);
                        writeJson(ex, 404, Map.of("error", "Token not found"));
                    }
                    return;
                }

                if ("POST".equals(method) && segments.length == 5 && "revoke".equals(segments[4])) {
                    log.info("[Storage] revokeAuthToken: tokenId=" + tokenValue);
                    storageService.revokeAuthToken(tokenValue);
                    writeJson(ex, 200, Map.of("status", "ok"));
                    return;
                }

                if ("GET".equals(method) && segments.length == 5 && "revoked".equals(segments[4])) {
                    boolean revoked = storageService.isTokenRevoked(tokenValue);
                    log.info("[Storage] isTokenRevoked: tokenId=" + tokenValue + " revoked=" + revoked);
                    writeJson(ex, 200, Map.of("revoked", revoked));
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            log.severe("[Storage] handleAuthTokens 500: " + e.getMessage());
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

            if ("POST".equals(method) && segments.length == 3) {
                Device device = readBody(ex, Device.class);
                log.info("[Storage] saveDevice: deviceId=" + device.deviceId()
                        + " type=" + device.type() + " owner=" + device.ownerId()
                        + " status=" + device.status());
                storageService.saveDevice(device);
                writeJson(ex, 201, device);
                return;
            }

            if ("PUT".equals(method) && segments.length == 3) {
                Device device = readBody(ex, Device.class);
                log.info("[Storage] updateDevice: deviceId=" + device.deviceId()
                        + " status=" + device.status());
                storageService.updateDevice(device);
                writeJson(ex, 200, device);
                return;
            }

            if ("GET".equals(method)) {
                if (segments.length == 5 && "owner".equals(segments[3])) {
                    String ownerId = segments[4];
                    log.info("[Storage] findDevicesByOwner: ownerId=" + ownerId);
                    List<Device> devices = storageService.findDevicesByOwner(ownerId);
                    log.info("[Storage] findDevicesByOwner: found " + devices.size()
                            + " devices for ownerId=" + ownerId);
                    writeJson(ex, 200, devices);
                    return;
                }
                if (segments.length == 4) {
                    String deviceId = segments[3];
                    log.info("[Storage] findDeviceById: deviceId=" + deviceId);
                    Optional<Device> device = storageService.findDeviceById(deviceId);
                    if (device.isPresent()) {
                        writeJson(ex, 200, device.get());
                    } else {
                        log.fine("[Storage] findDeviceById: not found deviceId=" + deviceId);
                        writeJson(ex, 404, Map.of("error", "Device not found"));
                    }
                    return;
                }
            }

            if ("DELETE".equals(method) && segments.length == 4) {
                String deviceId = segments[3];
                log.info("[Storage] deleteDevice: deviceId=" + deviceId);
                storageService.deleteDevice(deviceId);
                writeJson(ex, 204, Map.of("status", "ok"));
                return;
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (DeviceNotFoundException e) {
            log.warning("[Storage] device not found: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warning("[Storage] saveDevice conflict: " + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[Storage] handleDevices 500: " + e.getMessage());
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

            if ("POST".equals(method) && segments.length == 3) {
                SecurityEvent event = readBody(ex, SecurityEvent.class);
                log.info("[Storage] saveEvent: eventId=" + event.eventId()
                        + " type=" + event.type() + " device=" + event.deviceId());
                storageService.saveEvent(event);
                writeJson(ex, 201, event);
                return;
            }

            if ("GET".equals(method)) {
                Map<String, String> params = parseQuery(query);

                if (segments.length == 5 && "device".equals(segments[3])) {
                    String deviceId = segments[4];
                    Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
                    Instant to   = Instant.parse(params.getOrDefault("to",   "2099-12-31T23:59:59Z"));
                    int max      = Integer.parseInt(params.getOrDefault("max", "500"));
                    log.info("[Storage] findEventsByDevice: deviceId=" + deviceId
                            + " from=" + from + " to=" + to + " max=" + max);
                    List<SecurityEvent> events = storageService.findEventsByDevice(deviceId, from, to, max);
                    log.info("[Storage] findEventsByDevice: returned " + events.size()
                            + " events for deviceId=" + deviceId);
                    writeJson(ex, 200, events);
                    return;
                }

                if (segments.length == 7 && "owner".equals(segments[3]) && "type".equals(segments[5])) {
                    String ownerId   = segments[4];
                    String eventType = segments[6];
                    Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
                    Instant to   = Instant.parse(params.getOrDefault("to",   "2099-12-31T23:59:59Z"));
                    int max      = Integer.parseInt(params.getOrDefault("max", "500"));
                    log.info("[Storage] findEventsByOwnerAndType: ownerId=" + ownerId
                            + " type=" + eventType + " max=" + max);
                    List<SecurityEvent> events = storageService.findEventsByOwnerAndType(
                            ownerId, eventType, from, to, max);
                    log.info("[Storage] findEventsByOwnerAndType: returned " + events.size() + " events");
                    writeJson(ex, 200, events);
                    return;
                }

                if (segments.length == 4) {
                    String eventId = segments[3];
                    log.info("[Storage] findEventById: eventId=" + eventId);
                    Optional<SecurityEvent> event = storageService.findEventById(eventId);
                    if (event.isPresent()) {
                        writeJson(ex, 200, event.get());
                    } else {
                        log.fine("[Storage] findEventById: not found eventId=" + eventId);
                        writeJson(ex, 404, Map.of("error", "Event not found"));
                    }
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            log.severe("[Storage] handleEvents 500: " + e.getMessage());
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
                Map body = readBody(ex, Map.class);
                VideoClip clip = JsonUtil.fromJson(JsonUtil.toJson(body.get("clip")), VideoClip.class);
                String b64 = (String) body.get("bytes");
                byte[] rawBytes = Base64.getDecoder().decode(b64);
                log.info("[Storage] saveVideoClip: clipId=" + clip.clipId()
                        + " deviceId=" + clip.deviceId() + " bytes=" + rawBytes.length);
                storageService.saveVideoClip(clip, rawBytes);
                writeJson(ex, 201, clip);
                return;
            }

            if ("GET".equals(method)) {
                Map<String, String> params = parseQuery(query);

                if (segments.length == 5 && "device".equals(segments[3])) {
                    String deviceId = segments[4];
                    Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
                    Instant to   = Instant.parse(params.getOrDefault("to",   "2099-12-31T23:59:59Z"));
                    log.info("[Storage] findClipsByDevice: deviceId=" + deviceId
                            + " from=" + from + " to=" + to);
                    List<VideoClip> clips = storageService.findClipsByDevice(deviceId, from, to);
                    log.info("[Storage] findClipsByDevice: returned " + clips.size()
                            + " clips for deviceId=" + deviceId);
                    writeJson(ex, 200, clips);
                    return;
                }

                if (segments.length == 4) {
                    String clipId = segments[3];
                    log.info("[Storage] findClipById: clipId=" + clipId);
                    Optional<VideoClip> clip = storageService.findClipById(clipId);
                    if (clip.isPresent()) {
                        writeJson(ex, 200, clip.get());
                    } else {
                        log.fine("[Storage] findClipById: not found clipId=" + clipId);
                        writeJson(ex, 404, Map.of("error", "Clip not found"));
                    }
                    return;
                }
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            log.severe("[Storage] handleVideoClips 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleVideoBytes(HttpExchange ex) throws IOException {
        try {
            String[] segments = ex.getRequestURI().getPath().split("/");
            if ("GET".equals(ex.getRequestMethod()) && segments.length == 4) {
                String storageKey = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);
                log.info("[Storage] loadVideoBytes: storageKey=" + storageKey);
                byte[] data = storageService.loadVideoBytes(storageKey);
                log.info("[Storage] loadVideoBytes: returned " + data.length
                        + " bytes for key=" + storageKey);
                String b64 = Base64.getEncoder().encodeToString(data);
                writeJson(ex, 200, Map.of("bytes", b64));
                return;
            }
            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (VideoNotFoundException e) {
            log.warning("[Storage] loadVideoBytes not found: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[Storage] handleVideoBytes 500: " + e.getMessage());
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

            if (segments.length < 5) {
                writeJson(ex, 400, Map.of("error", "Bad request"));
                return;
            }

            String deviceTypeKey = segments[3];

            if ("latest".equals(segments[4]) && "GET".equals(method)) {
                log.info("[Storage] getLatestFirmwareVersion: deviceType=" + deviceTypeKey);
                String version = storageService.getLatestFirmwareVersion(deviceTypeKey);
                if (version != null) {
                    log.info("[Storage] latestFirmware: deviceType=" + deviceTypeKey
                            + " version=" + version);
                    writeJson(ex, 200, Map.of("version", version));
                } else {
                    log.warning("[Storage] latestFirmware: no firmware for type=" + deviceTypeKey);
                    writeJson(ex, 404, Map.of("error", "No firmware for type: " + deviceTypeKey));
                }
                return;
            }

            String firmwareVersion = segments[4];

            if ("GET".equals(method)) {
                log.info("[Storage] loadFirmwareBinary: deviceType=" + deviceTypeKey
                        + " version=" + firmwareVersion);
                byte[] binary = storageService.loadFirmwareBinary(deviceTypeKey, firmwareVersion);
                log.info("[Storage] loadFirmwareBinary: returned " + binary.length
                        + " bytes for " + deviceTypeKey + "/" + firmwareVersion);
                writeJson(ex, 200, Map.of("bytes", Base64.getEncoder().encodeToString(binary)));
                return;
            }

            if ("POST".equals(method)) {
                Map body = readBody(ex, Map.class);
                byte[] binary = Base64.getDecoder().decode((String) body.get("bytes"));
                log.info("[Storage] saveFirmwareBinary: deviceType=" + deviceTypeKey
                        + " version=" + firmwareVersion + " bytes=" + binary.length);
                storageService.saveFirmwareBinary(deviceTypeKey, firmwareVersion, binary);
                writeJson(ex, 201, Map.of("status", "ok"));
                return;
            }

            writeJson(ex, 405, Map.of("error", "Method not allowed"));
        } catch (IllegalArgumentException e) {
            log.warning("[Storage] firmware not found: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[Storage] handleFirmware 500: " + e.getMessage());
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

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String userId   = (String) body.get("userId");
                String platform = (String) body.get("platform");
                log.info("[Storage] savePushToken: userId=" + userId + " platform=" + platform);
                storageService.savePushToken(userId, (String) body.get("token"), platform);
                writeJson(ex, 201, Map.of("status", "ok"));
                return;
            }

            if ("DELETE".equals(method) && segments.length == 4) {
                String token = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);
                log.info("[Storage] deletePushToken: token=" + token);
                storageService.deletePushToken(token);
                writeJson(ex, 200, Map.of("status", "ok"));
                return;
            }

            if ("GET".equals(method) && segments.length == 5 && "user".equals(segments[3])) {
                String userId = segments[4];
                log.info("[Storage] findPushTokensByUser: userId=" + userId);
                List<String> tokens = storageService.findPushTokensByUser(userId);
                log.info("[Storage] findPushTokensByUser: found " + tokens.size()
                        + " tokens for userId=" + userId);
                writeJson(ex, 200, tokens);
                return;
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (Exception e) {
            log.severe("[Storage] handlePushTokens 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Registration token handlers
    // =====================================================================

    private void handleRegistrationTokens(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String deviceId = (String) body.get("deviceId");
                log.info("[Storage] saveRegistrationToken: deviceId=" + deviceId);
                storageService.saveRegistrationToken(deviceId, (String) body.get("registrationToken"));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 4) {
                String deviceId = segments[3];
                log.info("[Storage] findRegistrationToken: deviceId=" + deviceId);
                Optional<String> token = storageService.findRegistrationToken(deviceId);
                if (token.isPresent()) {
                    writeJson(ex, 200, Map.of("registrationToken", token.get()));
                } else {
                    log.fine("[Storage] findRegistrationToken: not found deviceId=" + deviceId);
                    writeJson(ex, 404, Map.of("error", "No registration token found"));
                }
            } else if ("DELETE".equals(method) && segments.length == 4) {
                String deviceId = segments[3];
                log.info("[Storage] deleteRegistrationToken: deviceId=" + deviceId);
                storageService.deleteRegistrationToken(deviceId);
                writeJson(ex, 200, Map.of("status", "ok"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handleRegistrationTokens 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Device heartbeat handlers
    // =====================================================================

    private void handleDeviceHeartbeats(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String deviceId = (String) body.get("deviceId");
                String heartbeatAt = (String) body.get("heartbeatAt");
                log.fine("[Storage] saveDeviceHeartbeat: deviceId=" + deviceId
                        + " at=" + heartbeatAt);
                storageService.saveDeviceHeartbeat(deviceId, Instant.parse(heartbeatAt));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 4) {
                String deviceId = segments[3];
                log.fine("[Storage] findDeviceHeartbeat: deviceId=" + deviceId);
                Optional<Instant> hb = storageService.findDeviceHeartbeat(deviceId);
                if (hb.isPresent()) {
                    writeJson(ex, 200, Map.of("heartbeatAt", hb.get().toString()));
                } else {
                    writeJson(ex, 404, Map.of("error", "No heartbeat found"));
                }
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handleDeviceHeartbeats 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // EPS dedup handlers
    // =====================================================================

    private void handleEpsDedup(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String dedupKey = (String) body.get("dedupKey");
                String eventId  = (String) body.get("eventId");
                log.info("[Storage] saveDeduplicationEntry: dedupKey=" + dedupKey
                        + " eventId=" + eventId);
                storageService.saveDeduplicationEntry(dedupKey, eventId,
                        Instant.parse((String) body.get("recordedAt")));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("POST".equals(method) && segments.length == 4 && "cleanup".equals(segments[3])) {
                Map body = readBody(ex, Map.class);
                Instant olderThan = Instant.parse((String) body.get("olderThan"));
                int deleted = storageService.deleteExpiredDeduplicationEntries(olderThan);
                log.info("[Storage] deleteExpiredDedup: deleted=" + deleted
                        + " olderThan=" + olderThan);
                writeJson(ex, 200, Map.of("deleted", deleted));
            } else if ("GET".equals(method) && segments.length == 4) {
                String key = URLDecoder.decode(segments[3], StandardCharsets.UTF_8);
                log.fine("[Storage] findDeduplicationEntry: key=" + key);
                Optional<Map<String, String>> entry = storageService.findDeduplicationEntry(key);
                if (entry.isPresent()) {
                    writeJson(ex, 200, entry.get());
                } else {
                    writeJson(ex, 404, Map.of("error", "Dedup entry not found"));
                }
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handleEpsDedup 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // EPS motion cooldown handlers
    // =====================================================================

    private void handleEpsMotionCooldown(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String deviceId = (String) body.get("deviceId");
                log.info("[Storage] saveMotionCooldown: deviceId=" + deviceId);
                storageService.saveMotionCooldown(deviceId, Instant.parse((String) body.get("alertAt")));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 4) {
                String deviceId = segments[3];
                log.fine("[Storage] findMotionCooldown: deviceId=" + deviceId);
                Optional<Instant> cd = storageService.findMotionCooldown(deviceId);
                if (cd.isPresent()) {
                    writeJson(ex, 200, Map.of("alertAt", cd.get().toString()));
                } else {
                    writeJson(ex, 404, Map.of("error", "No motion cooldown found"));
                }
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handleEpsMotionCooldown 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // EPS Lamport clock handlers
    // =====================================================================

    private void handleEpsLamportClock(HttpExchange ex) throws IOException {
        try {
            String method    = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String nodeId = (String) body.get("nodeId");
                long value    = ((Number) body.get("value")).longValue();
                log.info("[Storage] saveLamportClock: nodeId=" + nodeId + " value=" + value);
                storageService.saveLamportClock(nodeId, value);
                writeJson(ex, 201, Map.of("status", "ok"));

            } else if ("POST".equals(method) && segments.length == 4
                    && "increment".equals(segments[3])) {
                Map body       = readBody(ex, Map.class);
                String nodeId  = (String) body.get("nodeId");
                long candidate = ((Number) body.get("candidate")).longValue();
                long newValue  = storageService.incrementAndGetLamportClock(nodeId, candidate);
                log.info("[Storage] incrementAndGetLamportClock: nodeId=" + nodeId
                        + " candidate=" + candidate + " result=" + newValue);
                writeJson(ex, 200, Map.of("value", newValue));

            } else if ("GET".equals(method) && segments.length == 4) {
                String nodeId = segments[3];
                long value    = storageService.findLamportClock(nodeId);
                log.info("[Storage] findLamportClock: nodeId=" + nodeId + " value=" + value);
                writeJson(ex, 200, Map.of("value", value));

            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handleEpsLamportClock 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Recording session handlers
    // =====================================================================

    private void handleRecordingSessions(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String sessionId = (String) body.get("sessionId");
                String deviceId  = (String) body.get("deviceId");
                log.info("[Storage] saveRecordingSession: sessionId=" + sessionId
                        + " deviceId=" + deviceId);
                storageService.saveRecordingSession(sessionId, deviceId,
                        (String) body.get("ownerId"), Instant.parse((String) body.get("startedAt")));
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 5 && "device".equals(segments[3])) {
                String deviceId = segments[4];
                log.info("[Storage] findActiveSessionForDevice: deviceId=" + deviceId);
                Optional<String> sid = storageService.findActiveSessionForDevice(deviceId);
                if (sid.isPresent()) {
                    writeJson(ex, 200, Map.of("sessionId", sid.get()));
                } else {
                    writeJson(ex, 404, Map.of("error", "No active session"));
                }
            } else if ("GET".equals(method) && segments.length == 4) {
                String sessionId = segments[3];
                log.info("[Storage] findRecordingSession: sessionId=" + sessionId);
                Optional<Map<String, String>> session = storageService.findRecordingSession(sessionId);
                if (session.isPresent()) {
                    writeJson(ex, 200, session.get());
                } else {
                    writeJson(ex, 404, Map.of("error", "Session not found"));
                }
            } else if ("DELETE".equals(method) && segments.length == 4) {
                String sessionId = segments[3];
                log.info("[Storage] deleteRecordingSession: sessionId=" + sessionId);
                storageService.deleteRecordingSession(sessionId);
                writeJson(ex, 200, Map.of("status", "ok"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handleRecordingSessions 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Notification outbox handlers
    // =====================================================================

    private void handleNotificationOutbox(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                String notificationId = (String) body.get("notificationId");
                int attempts = ((Number) body.get("attempts")).intValue();
                log.info("[Storage] saveNotificationOutbox: notificationId=" + notificationId
                        + " attempts=" + attempts);
                storageService.saveNotificationOutbox(notificationId,
                        (String) body.get("token"), (String) body.get("payload"), attempts);
                writeJson(ex, 201, Map.of("status", "ok"));
            } else if ("POST".equals(method) && segments.length == 5 && "attempts".equals(segments[4])) {
                Map body = readBody(ex, Map.class);
                int newAttempts = ((Number) body.get("attempts")).intValue();
                log.info("[Storage] updateNotificationAttempts: notificationId=" + segments[3]
                        + " attempts=" + newAttempts);
                storageService.updateNotificationAttempts(segments[3], newAttempts);
                writeJson(ex, 200, Map.of("status", "ok"));
            } else if ("GET".equals(method) && segments.length == 3) {
                Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
                int max = Integer.parseInt(params.getOrDefault("max", "50"));
                log.fine("[Storage] findPendingNotifications: max=" + max);
                List<Map<String, Object>> pending = storageService.findPendingNotifications(max);
                log.info("[Storage] findPendingNotifications: found " + pending.size() + " pending");
                writeJson(ex, 200, pending);
            } else if ("DELETE".equals(method) && segments.length == 4) {
                String notificationId = segments[3];
                log.info("[Storage] deleteNotificationOutbox: notificationId=" + notificationId);
                storageService.deleteNotificationOutbox(notificationId);
                writeJson(ex, 200, Map.of("status", "ok"));
            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handleNotificationOutbox 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handlePendingCommands(HttpExchange ex) throws IOException {
        try {
            String method   = ex.getRequestMethod();
            String[] segments = ex.getRequestURI().getPath().split("/");

            // POST /storage/pending-commands  — save
            if ("POST".equals(method) && segments.length == 3) {
                Map body = readBody(ex, Map.class);
                storageService.savePendingCommand(
                        (String) body.get("correlationId"),
                        (String) body.get("deviceId"),
                        (String) body.get("commandType"),
                        Instant.parse((String) body.get("dispatchedAt")),
                        Instant.parse((String) body.get("expiresAt")));
                log.info("[Storage] savePendingCommand: correlationId=" + body.get("correlationId"));
                writeJson(ex, 201, Map.of("status", "ok"));

                // POST /storage/pending-commands/cleanup  — expire
            } else if ("POST".equals(method) && segments.length == 4
                    && "cleanup".equals(segments[3])) {
                Map body = readBody(ex, Map.class);
                int deleted = storageService.deleteExpiredPendingCommands(
                        Instant.parse((String) body.get("olderThan")));
                log.info("[Storage] deleteExpiredPendingCommands: deleted=" + deleted);
                writeJson(ex, 200, Map.of("deleted", deleted));

                // POST /storage/pending-commands/{id}/result  — update result
            } else if ("POST".equals(method) && segments.length == 5
                    && "result".equals(segments[4])) {
                Map body = readBody(ex, Map.class);
                String result = (String) body.get("result");
                log.info("[Storage] updatePendingCommandResult: correlationId=" + segments[3]
                        + " result=" + result);
                storageService.updatePendingCommandResult(segments[3], result);
                writeJson(ex, 200, Map.of("status", "ok"));

                // GET /storage/pending-commands/{id}  — find
            } else if ("GET".equals(method) && segments.length == 4) {
                log.info("[Storage] findPendingCommand: correlationId=" + segments[3]);
                Optional<Map<String, String>> cmd = storageService.findPendingCommand(segments[3]);
                if (cmd.isPresent()) writeJson(ex, 200, cmd.get());
                else writeJson(ex, 404, Map.of("error", "Pending command not found"));

                // DELETE /storage/pending-commands/{id}  — delete
            } else if ("DELETE".equals(method) && segments.length == 4) {
                log.info("[Storage] deletePendingCommand: correlationId=" + segments[3]);
                storageService.deletePendingCommand(segments[3]);
                writeJson(ex, 200, Map.of("status", "ok"));

            } else {
                writeJson(ex, 400, Map.of("error", "Bad request"));
            }
        } catch (Exception e) {
            log.severe("[Storage] handlePendingCommands 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
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