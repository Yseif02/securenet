package com.securenet.notification.server;

import com.securenet.common.JsonUtil;
import com.securenet.model.EventType;
import com.securenet.model.SecurityEvent;
import com.securenet.notification.NotificationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * HTTP server for the Notification Service.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /notify/register-token} — register a push token</li>
 *   <li>{@code POST /notify/deregister-token} — remove a push token</li>
 *   <li>{@code POST /notify/alert} — dispatch an event alert (called by EPS)</li>
 * </ul>
 */
public class NotificationServer {

    private final String host;
    private final int port;
    private final NotificationService service;
    private HttpServer httpServer;

    public NotificationServer(String host, int port, NotificationService service) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.service = Objects.requireNonNull(service, "service");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(4));

        httpServer.createContext("/notify/register-token", this::handleRegisterToken);
        httpServer.createContext("/notify/deregister-token", this::handleDeregisterToken);
        httpServer.createContext("/notify/alert", this::handleAlert);
        httpServer.createContext("/health", ex -> writeJson(ex, 200, Map.of("status", "UP")));

        httpServer.start();
        System.out.println("[NotificationService] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[NotificationService] stopped");
        }
    }

    private void handleRegisterToken(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed")); return;
        }
        try {
            Map body = readBody(ex, Map.class);
            service.registerPushToken(
                    (String) body.get("userId"),
                    (String) body.get("pushToken"),
                    (String) body.get("platform"));
            writeJson(ex, 201, Map.of("status", "ok"));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleDeregisterToken(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed")); return;
        }
        try {
            Map body = readBody(ex, Map.class);
            service.deregisterPushToken((String) body.get("pushToken"));
            writeJson(ex, 200, Map.of("status", "ok"));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleAlert(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed")); return;
        }
        try {
            Map body = readBody(ex, Map.class);
            SecurityEvent event = new SecurityEvent(
                    (String) body.get("eventId"),
                    (String) body.get("deviceId"),
                    (String) body.get("ownerId"),
                    EventType.valueOf((String) body.get("eventType")),
                    Instant.parse((String) body.get("occurredAt")),
                    Map.of()
            );
            service.sendEventAlert(event);
            writeJson(ex, 202, Map.of("status", "dispatched"));
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
}
