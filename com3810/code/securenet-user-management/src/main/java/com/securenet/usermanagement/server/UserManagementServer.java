package com.securenet.usermanagement.server;

import com.securenet.common.JsonUtil;
import com.securenet.model.AuthToken;
import com.securenet.model.User;
import com.securenet.model.exception.AuthenticationException;
import com.securenet.usermanagement.UserManagementService;
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

/**
 * HTTP server for the User Management Service.
 *
 * <p>Exposes registration, login, token validation, and profile management
 * endpoints. The API Gateway calls {@code POST /ums/validate-token} on
 * every inbound request.
 */
public class UserManagementServer {

    private final String host;
    private final int port;
    private final UserManagementService service;
    private HttpServer httpServer;

    /**
     * @param host    bind address
     * @param port    port to listen on
     * @param service the UMS business logic
     */
    public UserManagementServer(String host, int port, UserManagementService service) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.service = Objects.requireNonNull(service, "service");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));

        httpServer.createContext("/ums/register", this::handleRegister);
        httpServer.createContext("/ums/login", this::handleLogin);
        httpServer.createContext("/ums/validate-token", this::handleValidateToken);
        httpServer.createContext("/ums/revoke-token", this::handleRevokeToken);
        httpServer.createContext("/ums/users", this::handleUsers);
        httpServer.createContext("/health", ex -> writeJson(ex, 200, Map.of("status", "UP")));

        httpServer.start();
        System.out.println("[UserManagementService] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[UserManagementService] stopped");
        }
    }

    // ----- Registration -----

    private void handleRegister(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            User user = service.registerUser(
                    (String) body.get("email"),
                    (String) body.get("displayName"),
                    (String) body.get("password")
            );
            writeJson(ex, 201, user);
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Login -----

    private void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            AuthToken token = service.login(
                    (String) body.get("email"),
                    (String) body.get("password")
            );
            writeJson(ex, 200, token);
        } catch (AuthenticationException e) {
            writeJson(ex, 401, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Token validation -----

    private void handleValidateToken(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            AuthToken token = service.validateToken((String) body.get("token"));
            writeJson(ex, 200, token);
        } catch (AuthenticationException e) {
            writeJson(ex, 401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- Token revocation -----

    private void handleRevokeToken(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            AuthToken token = readBody(ex, AuthToken.class);
            service.revokeToken(token);
            writeJson(ex, 204, Map.of("status", "ok"));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ----- User profile -----

    private void handleUsers(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String[] segments = path.split("/");

            // GET /ums/users/{id}
            if ("GET".equals(method) && segments.length == 4) {
                String userId = segments[3];
                var user = service.getUserById(userId);
                if (user.isPresent()) {
                    writeJson(ex, 200, user.get());
                } else {
                    writeJson(ex, 404, Map.of("error", "User not found"));
                }
                return;
            }

            // PUT /ums/users/{id}/display-name
            if ("PUT".equals(method) && segments.length == 5 && "display-name".equals(segments[4])) {
                Map body = readBody(ex, Map.class);
                User updated = service.updateDisplayName(segments[3], (String) body.get("displayName"));
                writeJson(ex, 200, updated);
                return;
            }

            // DELETE /ums/users/{id}
            if ("DELETE".equals(method) && segments.length == 4) {
                service.deactivateUser(segments[3]);
                writeJson(ex, 204, Map.of("status", "ok"));
                return;
            }

            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
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
}
