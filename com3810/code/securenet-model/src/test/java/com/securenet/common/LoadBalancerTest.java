package com.securenet.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerTest {

    private HttpServer service200;
    private HttpServer service500;
    private HttpServer clusterManager;

    @AfterEach
    void tearDown() {
        stop(service200);
        stop(service500);
        stop(clusterManager);
    }

    @Test
    void runHealthChecks_marksHealthyAndUnhealthyUrls() throws Exception {
        service200 = startServer(200, "{\"status\":\"UP\"}");
        service500 = startServer(500, "{\"status\":\"DOWN\"}");

        LoadBalancer lb = new LoadBalancer("Test",
                List.of(url(service200), url(service500)));

        invoke(lb, "runHealthChecks");

        Map<String, Boolean> status = lb.getStatus();
        assertEquals(Boolean.TRUE, status.get(url(service200)));
        assertEquals(Boolean.FALSE, status.get(url(service500)));
    }

    @Test
    void pollClusterManager_addsNewUrlsAndRemovesMissingOnes() throws Exception {
        service200 = startServer(200, "{\"status\":\"UP\"}");
        HttpServer serviceNew = startServer(200, "{\"status\":\"UP\"}");
        try {
            String existingUrl = url(service200);
            String newUrl = url(serviceNew);
            String clusterBody = JsonUtil.toJson(Map.of(
                    "svc-2", Map.of("service", "Test", "url", newUrl, "status", "HEALTHY")
            ));
            clusterManager = startServer("/cluster/status", 200, clusterBody);

            LoadBalancer lb = new LoadBalancer("Test", List.of(existingUrl));
            lb.watchClusterManager(clusterManagerBase(), "Test");

            invoke(lb, "pollClusterManager", "Test");

            Map<String, Boolean> status = lb.getStatus();
            assertFalse(status.containsKey(existingUrl));
            assertEquals(Boolean.TRUE, status.get(newUrl));
        } finally {
            serviceNew.stop(0);
        }
    }

    @Test
    void pollClusterManager_fallsBackToNextClusterManagerUrl() throws Exception {
        service200 = startServer(200, "{\"status\":\"UP\"}");
        String clusterBody = JsonUtil.toJson(Map.of(
                "svc-1", Map.of("service", "Test", "url", url(service200), "status", "HEALTHY")
        ));
        clusterManager = startServer("/cluster/status", 200, clusterBody);

        LoadBalancer lb = new LoadBalancer("Test", List.of("http://localhost:65530"));
        lb.watchClusterManager("http://localhost:65531," + clusterManagerBase(), "Test");

        invoke(lb, "pollClusterManager", "Test");

        Map<String, Boolean> status = lb.getStatus();
        assertEquals(Boolean.TRUE, status.get(url(service200)));
    }

    @Test
    void nextHealthyUrl_throwsWhenNoHealthyInstancesExist() {
        LoadBalancer lb = new LoadBalancer("Empty", List.of());
        RuntimeException error = assertThrows(RuntimeException.class, lb::nextHealthyUrl);
        assertTrue(error.getMessage().contains("No healthy instances"));
    }

    private String clusterManagerBase() {
        return "http://localhost:" + clusterManager.getAddress().getPort();
    }

    private static HttpServer startServer(int statusCode, String responseBody) throws Exception {
        return startServer("/health", statusCode, responseBody);
    }

    private static HttpServer startServer(String path, int statusCode, String responseBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void invoke(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }
        Method method = LoadBalancer.class.getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static void stop(HttpServer server) {
        if (server != null) {
            server.stop(0);
        }
    }
}
