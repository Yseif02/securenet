package com.securenet.videostreaming.server;

import com.securenet.common.JsonUtil;
import com.securenet.model.VideoClip;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.VideoNotFoundException;
import com.securenet.videostreaming.VideoStreamingService;
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
 * HTTP server for the Video Streaming Service.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /vss/session/open} — open recording session</li>
 *   <li>{@code POST /vss/session/close} — close recording session</li>
 *   <li>{@code POST /vss/chunks/ingest} — ingest a video chunk</li>
 *   <li>{@code GET  /vss/clips/device/{deviceId}?from=&to=} — find clips</li>
 *   <li>{@code GET  /vss/clips/{clipId}/playback-url?validFor=} — signed URL</li>
 * </ul>
 */
public class VideoStreamingServer {

    private final String host;
    private final int port;
    private final VideoStreamingService service;
    private HttpServer httpServer;

    public VideoStreamingServer(String host, int port, VideoStreamingService service) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.service = Objects.requireNonNull(service, "service");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));

        httpServer.createContext("/vss/session/open", this::handleOpenSession);
        httpServer.createContext("/vss/session/close", this::handleCloseSession);
        httpServer.createContext("/vss/chunks/ingest", this::handleIngestChunk);
        httpServer.createContext("/vss/clips/device", this::handleFindClips);
        httpServer.createContext("/vss/clips/playback", this::handlePlaybackUrl);
        httpServer.createContext("/health", ex -> writeJson(ex, 200, Map.of("status", "UP")));

        httpServer.start();
        System.out.println("[VideoStreamingService] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[VideoStreamingService] stopped");
        }
    }

    private void handleOpenSession(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { writeJson(ex, 405, Map.of("error", "Method not allowed")); return; }
        try {
            Map body = readBody(ex, Map.class);
            String sessionId = service.openRecordingSession(
                    (String) body.get("deviceId"), (String) body.get("ownerId"));
            writeJson(ex, 201, Map.of("recordingSessionId", sessionId));
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleCloseSession(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { writeJson(ex, 405, Map.of("error", "Method not allowed")); return; }
        try {
            Map body = readBody(ex, Map.class);
            service.closeRecordingSession((String) body.get("recordingSessionId"));
            writeJson(ex, 200, Map.of("status", "closed"));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleIngestChunk(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { writeJson(ex, 405, Map.of("error", "Method not allowed")); return; }
        try {
            Map body = readBody(ex, Map.class);
            String sessionId = (String) body.get("recordingSessionId");
            long seq = ((Number) body.get("sequenceNumber")).longValue();
            byte[] bytes = Base64.getDecoder().decode((String) body.get("chunkBytes"));
            service.ingestChunk(sessionId, seq, bytes);
            writeJson(ex, 202, Map.of("status", "ingested"));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleFindClips(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { writeJson(ex, 405, Map.of("error", "Method not allowed")); return; }
        try {
            String[] segments = ex.getRequestURI().getPath().split("/");
            if (segments.length < 5) { writeJson(ex, 400, Map.of("error", "deviceId required")); return; }
            String deviceId = segments[4];
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
            Instant to = Instant.parse(params.getOrDefault("to", "2099-12-31T23:59:59Z"));
            List<VideoClip> clips = service.findClips(deviceId, from, to);
            writeJson(ex, 200, clips);
        } catch (DeviceNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handlePlaybackUrl(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { writeJson(ex, 405, Map.of("error", "Method not allowed")); return; }
        try {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            String clipId = params.get("clipId");
            int validFor = Integer.parseInt(params.getOrDefault("validFor", "3600"));
            String url = service.generateSignedPlaybackUrl(clipId, validFor);
            writeJson(ex, 200, Map.of("playbackUrl", url));
        } catch (VideoNotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private static <T> T readBody(HttpExchange ex, Class<T> clazz) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return JsonUtil.fromJson(new String(is.readAllBytes(), StandardCharsets.UTF_8), clazz);
        }
    }

    private static void writeJson(HttpExchange ex, int statusCode, Object body) throws IOException {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Map.of();
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }
}
