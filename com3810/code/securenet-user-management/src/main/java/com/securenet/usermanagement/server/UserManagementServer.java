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
import java.util.logging.Logger;

/**
 * HTTP server for the User Management Service.
 *
 * <p>Exposes registration, login, token validation, and profile management
 * endpoints. The API Gateway calls {@code POST /ums/validate-token} on
 * every inbound request.
 */
public class UserManagementServer {

    private static final Logger log = Logger.getLogger(UserManagementServer.class.getName());

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

        httpServer.createContext("/ums/register",       this::handleRegister);
        httpServer.createContext("/ums/login",          this::handleLogin);
        httpServer.createContext("/ums/validate-token", this::handleValidateToken);
        httpServer.createContext("/ums/revoke-token",   this::handleRevokeToken);
        httpServer.createContext("/ums/users",          this::handleUsers);
        httpServer.createContext("/health", ex -> {
            log.fine("[UMS] Health check from " + ex.getRemoteAddress());
            writeJson(ex, 200, Map.of("status", "UP"));
        });

        httpServer.start();
        log.info("[UMS] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[UMS] stopped");        }
    }

    // ----- Registration -----

    private void handleRegister(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body = readBody(ex, Map.class);
            String email = (String) body.get("email");
            log.info("[UMS] POST /ums/register email=" + email);
            User user = service.registerUser(
                    email,
                    (String) body.get("displayName"),
                    (String) body.get("password")
            );
            log.info("[UMS] Register OK: userId=" + user.userId() + " email=" + email);
            writeJson(ex, 201, user);
        } catch (IllegalArgumentException e) {
            log.warning("[UMS] Register 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[UMS] Register 500: " + e.getMessage());
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
            String email = (String) body.get("email");
            log.info("[UMS] POST /ums/login email=" + email);
            AuthToken token = service.login(email, (String) body.get("password"));
            log.info("[UMS] Login OK: userId=" + token.userId());
            writeJson(ex, 200, token);
        } catch (AuthenticationException e) {
            log.warning("[UMS] Login 401: " + e.getMessage());
            writeJson(ex, 401, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warning("[UMS] Login 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[UMS] Login 500: " + e.getMessage());
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
            String tokenValue = (String) body.get("token");
            log.info("[UMS] POST /ums/validate-token tokenId=" + tokenValue);
            AuthToken token = service.validateToken(tokenValue);
            log.info("[UMS] Token valid: userId=" + token.userId());
            writeJson(ex, 200, token);
        } catch (AuthenticationException e) {
            log.warning("[UMS] Validate-token 401: " + e.getMessage());
            writeJson(ex, 401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[UMS] Validate-token 500: " + e.getMessage());
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
            log.info("[UMS] POST /ums/revoke-token tokenId=" + token.tokenValue()
                    + " userId=" + token.userId());
            service.revokeToken(token);
            log.info("[UMS] Token revoked: tokenId=" + token.tokenValue());
            ex.sendResponseHeaders(204, -1);
            ex.getResponseBody().close();
        } catch (Exception e) {
            log.severe("[UMS] Revoke-token 500: " + e.getMessage());
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
                log.info("[UMS] GET /ums/users/" + userId);
                var user = service.getUserById(userId);
                if (user.isPresent()) {
                    log.info("[UMS] getUserById OK: userId=" + userId);
                    writeJson(ex, 200, user.get());
                } else {
                    log.warning("[UMS] getUserById 404: userId=" + userId);
                    writeJson(ex, 404, Map.of("error", "User not found"));
                }
                return;
            }

            // PUT /ums/users/{id}/display-name
            if ("PUT".equals(method) && segments.length == 5 && "display-name".equals(segments[4])) {
                String userId = segments[3];
                log.info("[UMS] PUT /ums/users/" + userId + "/display-name");
                Map body = readBody(ex, Map.class);
                User updated = service.updateDisplayName(userId, (String) body.get("displayName"));
                log.info("[UMS] Display name updated: userId=" + userId);
                writeJson(ex, 200, updated);
                return;
            }

            // DELETE /ums/users/{id}
            if ("DELETE".equals(method) && segments.length == 4) {
                String userId = segments[3];
                log.info("[UMS] DELETE /ums/users/" + userId);
                service.deactivateUser(userId);
                log.info("[UMS] User deactivated: userId=" + userId);
                writeJson(ex, 204, Map.of("status", "ok"));
                return;
            }

            log.warning("[UMS] Unmatched request: " + method + " " + path);
            writeJson(ex, 400, Map.of("error", "Bad request"));
        } catch (IllegalArgumentException e) {
            log.warning("[UMS] Users 400: " + e.getMessage());
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.severe("[UMS] Users 500: " + e.getMessage());
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