package com.securenet.testsupport;

import com.securenet.common.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    private TestHttpServer(HttpServer server) {
        this.server = server;
    }

    public static TestHttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "test-http-server");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return new TestHttpServer(server);
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void json(String path, int statusCode, Object body) {
        handle(path, exchange -> writeJsonResponse(exchange, statusCode, body));
    }

    public void text(String path, int statusCode, String body) {
        handle(path, exchange -> writeBytes(exchange, statusCode, body, "text/plain"));
    }

    public void handle(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void writeJsonResponse(HttpExchange exchange, int statusCode, Object body)
            throws IOException {
        writeBytes(exchange, statusCode, JsonUtil.toJson(body), "application/json");
    }

    private static void writeBytes(HttpExchange exchange,
                                   int statusCode,
                                   String body,
                                   String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } finally {
            exchange.close();
        }
    }

    public static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return JsonUtil.fromJson(body, Map.class);
    }

    public static void writeJson(HttpExchange exchange, int statusCode, Object body)
            throws IOException {
        writeJsonResponse(exchange, statusCode, body);
    }

    public static void writeText(HttpExchange exchange, int statusCode, String body)
            throws IOException {
        writeBytes(exchange, statusCode, body, "text/plain");
    }
}
