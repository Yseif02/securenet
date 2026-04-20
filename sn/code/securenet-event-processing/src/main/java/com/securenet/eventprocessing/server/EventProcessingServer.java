package com.securenet.eventprocessing.server;

import com.securenet.common.JsonUtil;
import com.securenet.eventprocessing.EventProcessingService;
import com.securenet.eventprocessing.impl.EventProcessingServiceImpl;
import com.securenet.model.EventType;
import com.securenet.model.SecurityEvent;
import com.securenet.model.exception.DeviceNotFoundException;
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
 * HTTP server for the Event Processing Service.
 *
 * <p>Exposes event ingestion (called by IDFS) and event history queries
 * (called by the API Gateway). When running in a Raft cluster, the
 * ingest endpoint returns 503 with a leader hint if this node is not
 * the current leader.
 */
public class EventProcessingServer {

    private static final Logger log = Logger.getLogger(EventProcessingServer.class.getName());

    private final String host;
    private final int port;
    private final EventProcessingService service;
    private HttpServer httpServer;

    public EventProcessingServer(String host, int port, EventProcessingService service) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.service = Objects.requireNonNull(service, "service");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));

        httpServer.createContext("/eps/events/ingest",  this::handleIngest);
        httpServer.createContext("/eps/events/device",  this::handleDeviceHistory);
        httpServer.createContext("/eps/events/owner",   this::handleOwnerFeed);
        httpServer.createContext("/eps/events/get",     this::handleGetEvent);
        httpServer.createContext("/health", ex -> {
            log.fine("[EPS] Health check from " + ex.getRemoteAddress());
            writeJson(ex, 200, Map.of("status", "UP"));
        });

        httpServer.start();
        log.info("[EventProcessingService] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[EventProcessingService] stopped");
        }
    }

    // =====================================================================
    // POST /eps/events/ingest
    // =====================================================================

    private void handleIngest(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);

            String deviceId    = (String) body.get("deviceId");
            EventType eventType = EventType.valueOf((String) body.get("eventType"));
            Instant occurredAt = Instant.parse((String) body.get("occurredAt"));

            Map<String, String> metadata = new HashMap<>();
            Object rawMetadata = body.get("metadata");
            if (rawMetadata instanceof Map) {
                ((Map<?, ?>) rawMetadata).forEach((k, v) ->
                        metadata.put(String.valueOf(k), String.valueOf(v)));
            }

            String nonce = (String) body.get("nonce");
            if (nonce != null && !nonce.isBlank()) {
                metadata.put("nonce", nonce);
            }

            log.info("[EPS] POST /eps/events/ingest deviceId=" + deviceId
                    + " type=" + eventType);

            SecurityEvent event = service.ingestEvent(deviceId, eventType, occurredAt, metadata);
            log.info("[EPS] Ingest accepted: eventId=" + (event != null ? event.eventId() : "null")
                    + " deviceId=" + deviceId + " type=" + eventType);
            writeJson(ex, 202, event);

        } catch (IllegalStateException e) {
            // Raft: not the leader
            log.warning("[EPS] Ingest rejected (not leader): " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            if (service instanceof EventProcessingServiceImpl epsImpl
                    && epsImpl.getRaftNode() != null) {
                String leaderId = epsImpl.getRaftNode().getLeaderId();
                if (leaderId != null) {
                    error.put("leaderId", leaderId);
                    log.info("[EPS] Redirecting client to leader: " + leaderId);
                }
            }
            writeJson(ex, 503, error);
        } catch (DeviceNotFoundException e) {
            log.warning("[EPS] Ingest 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warning("[EPS] Ingest 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[EPS] Ingest 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // GET /eps/events/device/{deviceId}
    // =====================================================================

    private void handleDeviceHistory(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            String[] segments = ex.getRequestURI().getPath().split("/");
            if (segments.length < 5) {
                writeJson(ex, 400, Map.of("error", "deviceId required in path"));
                return;
            }
            String deviceId = segments[4];
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
            Instant to   = Instant.parse(params.getOrDefault("to",   "2099-12-31T23:59:59Z"));
            int max      = Integer.parseInt(params.getOrDefault("max", "100"));

            log.info("[EPS] GET /eps/events/device/" + deviceId + " max=" + max);
            List<SecurityEvent> events = service.getEventHistory(deviceId, from, to, max);
            log.info("[EPS] Device history: deviceId=" + deviceId
                    + " returned=" + events.size());
            writeJson(ex, 200, events);

        } catch (DeviceNotFoundException e) {
            log.warning("[EPS] DeviceHistory 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[EPS] DeviceHistory 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // GET /eps/events/owner/{ownerId}/type/{type}
    // =====================================================================

    private void handleOwnerFeed(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            String[] segments = ex.getRequestURI().getPath().split("/");
            if (segments.length < 7 || !"type".equals(segments[5])) {
                writeJson(ex, 400, Map.of("error",
                        "Expected /eps/events/owner/{ownerId}/type/{type}"));
                return;
            }
            String ownerId   = segments[4];
            EventType type   = EventType.valueOf(segments[6]);
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
            Instant to   = Instant.parse(params.getOrDefault("to",   "2099-12-31T23:59:59Z"));
            int max      = Integer.parseInt(params.getOrDefault("max", "100"));

            log.info("[EPS] GET /eps/events/owner/" + ownerId + "/type/" + type + " max=" + max);
            List<SecurityEvent> events = service.getEventsByTypeForOwner(
                    ownerId, type, from, to, max);
            log.info("[EPS] Owner feed: ownerId=" + ownerId + " type=" + type
                    + " returned=" + events.size());
            writeJson(ex, 200, events);

        } catch (Exception e) {
            log.severe("[EPS] OwnerFeed 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // GET /eps/events/get?eventId=
    // =====================================================================

    private void handleGetEvent(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            String eventId = params.get("eventId");
            if (eventId == null || eventId.isBlank()) {
                writeJson(ex, 400, Map.of("error", "eventId required"));
                return;
            }
            log.info("[EPS] GET /eps/events/get eventId=" + eventId);
            SecurityEvent event = service.getEventById(eventId);
            if (event != null) {
                writeJson(ex, 200, event);
            } else {
                log.fine("[EPS] Event not found: eventId=" + eventId);
                writeJson(ex, 404, Map.of("error", "Event not found"));
            }
        } catch (Exception e) {
            log.severe("[EPS] GetEvent 500: " + e.getMessage());
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

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Map.of();
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }
}