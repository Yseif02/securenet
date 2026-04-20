package com.securenet.gateway.server;

import com.securenet.common.JsonUtil;
import com.securenet.gateway.APIGatewayService;
import com.securenet.model.AuthToken;
import com.securenet.model.exception.AuthenticationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * HTTP server for the API Gateway.
 *
 * <p>All client requests arrive here. The gateway authenticates the
 * bearer token, extracts the target service and endpoint from the
 * URL path, and routes to the appropriate downstream service.
 *
 * <p>URL pattern: {@code /api/{serviceName}/{endpoint...}}
 * <br>Example: {@code POST /api/device-management/devices/lock}
 */
public class APIGatewayServer {

    private static final Logger log = Logger.getLogger(APIGatewayServer.class.getName());

    private final String host;
    private final int port;
    private final APIGatewayService gateway;
    private HttpServer httpServer;

    public APIGatewayServer(String host, int port, APIGatewayService gateway) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(16));

        httpServer.createContext("/api/", this::handleApiRequest);
        httpServer.createContext("/health", ex -> {
            log.fine("[APIGateway] Health check from " + ex.getRemoteAddress());
            writeJson(ex, 200, Map.of("status", "UP"));
        });

        httpServer.start();
        log.info("[APIGateway] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            log.info("[APIGateway] stopped");
        }
    }

    /**
     * Handles all {@code /api/{serviceName}/{endpoint...}} requests.
     *
     * <p>Flow:
     * <ol>
     *   <li>Extract bearer token from Authorization header</li>
     *   <li>Authenticate via {@link APIGatewayService#authenticateRequest}</li>
     *   <li>Parse service name and endpoint from URL path</li>
     *   <li>Route to downstream service</li>
     *   <li>Return downstream response to client</li>
     * </ol>
     */
    private void handleApiRequest(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String clientAddr = ex.getRemoteAddress().toString();
        log.info("[APIGateway] Inbound: " + method + " " + path + " from " + clientAddr);

        try {
            // 1. Extract bearer token
            String authHeader = ex.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
                log.warning("[APIGateway] REJECTED " + method + " " + path
                        + ": missing Authorization header");
                writeJson(ex, 401, Map.of("error", "Missing Authorization header"));
                return;
            }
            String bearerToken = authHeader.substring(7).trim();

            // 2. Authenticate
            AuthToken token;
            try {
                token = gateway.authenticateRequest(bearerToken);
            } catch (AuthenticationException e) {
                log.warning("[APIGateway] REJECTED " + method + " " + path
                        + ": auth failed — " + e.getMessage());
                writeJson(ex, 401, Map.of("error", e.getMessage()));
                return;
            }

            // 3. Parse path: /api/{serviceName}/{endpoint...}
            String afterApi = path.substring("/api/".length());
            int slashIdx = afterApi.indexOf('/');
            if (slashIdx < 0) {
                log.warning("[APIGateway] REJECTED " + method + " " + path + ": malformed path");
                writeJson(ex, 400, Map.of("error", "Expected /api/{service}/{endpoint}"));
                return;
            }
            String serviceName = afterApi.substring(0, slashIdx);
            String endpoint = afterApi.substring(slashIdx);

            // Include query string in endpoint
            String query = ex.getRequestURI().getQuery();
            if (query != null) endpoint += "?" + query;

            // 4. Read body for POST/PUT
            String payload = null;
            if ("POST".equals(method) || "PUT".equals(method)) {
                try (InputStream is = ex.getRequestBody()) {
                    payload = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.info("[APIGateway] Payload: userId=" + token.userId()
                        + " -> " + serviceName + endpoint
                        + " payloadLen=" + (payload == null ? 0 : payload.length()));
            }

            // 5. Route
            log.info("[APIGateway] Dispatching: userId=" + token.userId()
                    + " " + method + " -> " + serviceName + endpoint);
            String responseBody = gateway.routeRequest(token, serviceName, endpoint, payload);
            log.info("[APIGateway] Completed: userId=" + token.userId()
                    + " " + method + " " + serviceName + endpoint
                    + " responseLen=" + responseBody.length());
            writeRaw(ex, 200, responseBody);

        } catch (APIGatewayService.ServiceUnavailableException e) {
            log.warning("[APIGateway] 503: service unavailable — " + e.getMessage()
                    + " for " + method + " " + path);
            writeJson(ex, 503, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[APIGateway] 500: unexpected error for " + method + " " + path
                    + " — " + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private static void writeJson(HttpExchange ex, int statusCode, Object body) throws IOException {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void writeRaw(HttpExchange ex, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}