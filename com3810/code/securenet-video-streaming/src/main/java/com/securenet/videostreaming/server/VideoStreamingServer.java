package com.securenet.videostreaming.server;

import com.securenet.common.JsonUtil;
import com.securenet.model.VideoClip;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.VideoNotFoundException;
import com.securenet.videostreaming.VideoStreamingService;
import com.securenet.videostreaming.impl.VideoStreamingServiceImpl;
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
 * HTTP server for the Video Streaming Service.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /vss/session/open}            — open recording session</li>
 *   <li>{@code POST /vss/session/close}           — close recording session</li>
 *   <li>{@code POST /vss/session/resume}          — resume after VSS failover</li>
 *   <li>{@code POST /vss/chunks/ingest}           — ingest a video chunk</li>
 *   <li>{@code GET  /vss/clips/device/{deviceId}} — find clips</li>
 *   <li>{@code GET  /vss/clips/playback}          — generate signed URL</li>
 * </ul>
 *
 * <h3>Chunk ack protocol</h3>
 * <p>{@code POST /vss/chunks/ingest} returns:
 * <pre>
 *   { "status": "ingested", "ackedThrough": 19 }   // checkpoint flushed
 *   { "status": "ingested", "ackedThrough": -1  }   // buffered, no flush yet
 * </pre>
 * When {@code ackedThrough >= 0}, the camera may delete local chunks
 * up to and including that sequence number.
 */
public class VideoStreamingServer {

    private static final Logger log = Logger.getLogger(VideoStreamingServer.class.getName());

    private final String host;
    private final int port;
    private final VideoStreamingServiceImpl service;

    /**
     * Own URL of this VSS instance (e.g. "http://localhost:9005").
     * Returned in /vss/session/resume responses so the camera knows
     * which instance to target for subsequent chunks.
     */
    private final String selfUrl;

    private HttpServer httpServer;

    /**
     * @param selfUrl the externally-reachable URL of this VSS instance,
     *                e.g. "http://localhost:9005". Passed via --self-url CLI arg.
     */
    public VideoStreamingServer(String host, int port,
                                VideoStreamingServiceImpl service,
                                String selfUrl) {
        this.host    = Objects.requireNonNull(host, "host");
        this.port    = port;
        this.service = Objects.requireNonNull(service, "service");
        this.selfUrl = Objects.requireNonNull(selfUrl, "selfUrl");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));

        httpServer.createContext("/vss/session/open",   this::handleOpenSession);
        httpServer.createContext("/vss/session/close",  this::handleCloseSession);
        httpServer.createContext("/vss/session/resume", this::handleResumeSession);
        httpServer.createContext("/vss/chunks/ingest",  this::handleIngestChunk);
        httpServer.createContext("/vss/clips/device",   this::handleFindClips);
        httpServer.createContext("/vss/clips/playback", this::handlePlaybackUrl);
        httpServer.createContext("/health", ex -> {
            log.fine("[VSS] Health check from " + ex.getRemoteAddress());
            writeJson(ex, 200, Map.of("status", "UP"));
        });

        httpServer.start();
        log.info("[VSS] started on " + host + ":" + port + " selfUrl=" + selfUrl);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[VSS] stopped");
        }
    }

    // =====================================================================
    // Session handlers
    // =====================================================================

    private void handleOpenSession(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body        = readBody(ex, Map.class);
            String deviceId = (String) body.get("deviceId");
            String ownerId  = (String) body.get("ownerId");
            log.info("[VSS] POST /vss/session/open deviceId=" + deviceId
                    + " ownerId=" + ownerId);
            String sessionId = service.openRecordingSession(deviceId, ownerId);
            log.info("[VSS] Session opened: sessionId=" + sessionId
                    + " deviceId=" + deviceId);
            writeJson(ex, 201, Map.of("recordingSessionId", sessionId));
        } catch (DeviceNotFoundException e) {
            log.warning("[VSS] OpenSession 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warning("[VSS] OpenSession 409: " + e.getMessage());
            writeJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[VSS] OpenSession 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleCloseSession(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body         = readBody(ex, Map.class);
            String sessionId = (String) body.get("recordingSessionId");
            log.info("[VSS] POST /vss/session/close sessionId=" + sessionId);
            service.closeRecordingSession(sessionId);
            log.info("[VSS] Session closed: sessionId=" + sessionId);
            writeJson(ex, 200, Map.of("status", "closed"));
        } catch (IllegalArgumentException e) {
            log.warning("[VSS] CloseSession 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[VSS] CloseSession 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Resume handshake called by a camera that lost its VSS connection.
     *
     * <p>Request: {@code POST /vss/session/resume}
     * <pre>{ "sessionId": "rec-..." }</pre>
     *
     * <p>Response:
     * <pre>{ "vssUrl": "http://localhost:9015", "resumeFrom": 19 }</pre>
     *
     * <p>The camera should resend all chunks from {@code resumeFrom + 1}
     * (i.e. everything above its last acked sequence number) to
     * {@code vssUrl}.
     */
    private void handleResumeSession(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body         = readBody(ex, Map.class);
            String sessionId = (String) body.get("sessionId");
            log.info("[VSS] POST /vss/session/resume sessionId=" + sessionId
                    + " selfUrl=" + selfUrl);
            Map<String, Object> result = service.resumeSession(sessionId, selfUrl);
            log.info("[VSS] Resume: sessionId=" + sessionId
                    + " resumeFrom=" + result.get("resumeFrom")
                    + " vssUrl=" + result.get("vssUrl"));
            writeJson(ex, 200, result);
        } catch (IllegalArgumentException e) {
            log.warning("[VSS] ResumeSession 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[VSS] ResumeSession 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Chunk handler
    // =====================================================================

    /**
     * Ingest a batch of chunks in a single POST and return the highest
     * sequence number that has been checkpointed to Postgres.
     *
     * <p>Request:
     * <pre>
     * {
     *   "recordingSessionId": "rec-...",
     *   "chunks": [
     *     { "sequenceNumber": 0, "chunkBytes": "&lt;base64&gt;" },
     *     { "sequenceNumber": 1, "chunkBytes": "&lt;base64&gt;" }
     *   ]
     * }
     * </pre>
     *
     * <p>Response:
     * <pre>
     *   { "status": "ingested", "ackedThrough": 9  }   // highest checkpointed seq in this batch
     *   { "status": "ingested", "ackedThrough": -1 }   // buffered only, no checkpoint yet
     * </pre>
     *
     * <p>IDFS publishes a stream ack to the camera only when {@code ackedThrough >= 0}.
     */
    private void handleIngestChunk(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body         = readBody(ex, Map.class);
            String sessionId = (String) body.get("recordingSessionId");
            List<Map> chunks = (List<Map>) body.get("chunks");

            if (sessionId == null || chunks == null || chunks.isEmpty()) {
                writeJson(ex, 400, Map.of("error",
                        "recordingSessionId and non-empty chunks are required"));
                return;
            }

            log.fine("[VSS] POST /vss/chunks/ingest sessionId=" + sessionId
                    + " batchSize=" + chunks.size());

            // Ingest each chunk; track the highest ackedThrough returned.
            // VSS checkpoints every chunk (CHECKPOINT_INTERVAL=1), so
            // ackedThrough will equal the last chunk's seq after all are ingested.
            long ackedThrough = -1L;
            for (Map chunk : chunks) {
                long   seq   = ((Number) chunk.get("sequenceNumber")).longValue();
                byte[] bytes = Base64.getDecoder().decode((String) chunk.get("chunkBytes"));
                long ack     = service.ingestChunkAndGetAck(sessionId, seq, bytes);
                if (ack > ackedThrough) ackedThrough = ack;
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("status",       "ingested");
            resp.put("ackedThrough", ackedThrough);
            writeJson(ex, 202, resp);
        } catch (IllegalArgumentException e) {
            log.warning("[VSS] IngestChunk 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[VSS] IngestChunk 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // Clip handlers
    // =====================================================================

    private void handleFindClips(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            String[] segments = ex.getRequestURI().getPath().split("/");
            if (segments.length < 5) {
                writeJson(ex, 400, Map.of("error", "deviceId required"));
                return;
            }
            String deviceId = segments[4];
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            Instant from = Instant.parse(params.getOrDefault("from", "1970-01-01T00:00:00Z"));
            Instant to   = Instant.parse(params.getOrDefault("to",   "2099-12-31T23:59:59Z"));
            log.info("[VSS] GET /vss/clips/device/" + deviceId
                    + " from=" + from + " to=" + to);
            List<VideoClip> clips = service.findClips(deviceId, from, to);
            log.info("[VSS] FindClips: deviceId=" + deviceId + " returned=" + clips.size());
            writeJson(ex, 200, clips);
        } catch (DeviceNotFoundException e) {
            log.warning("[VSS] FindClips 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[VSS] FindClips 500: " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handlePlaybackUrl(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            String clipId = params.get("clipId");
            int validFor  = Integer.parseInt(params.getOrDefault("validFor", "3600"));
            log.info("[VSS] GET /vss/clips/playback clipId=" + clipId
                    + " validFor=" + validFor);
            String url = service.generateSignedPlaybackUrl(clipId, validFor);
            log.info("[VSS] Playback URL generated: clipId=" + clipId);
            writeJson(ex, 200, Map.of("playbackUrl", url));
        } catch (VideoNotFoundException e) {
            log.warning("[VSS] PlaybackUrl 404: " + e.getMessage());
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[VSS] PlaybackUrl 500: " + e.getMessage());
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