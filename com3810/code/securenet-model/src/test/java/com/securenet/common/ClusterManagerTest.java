package com.securenet.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClusterManagerTest {

    private HttpServer healthServer;

    @Test
    void getStatus_reflectsHealthyAndFailedTransitions() throws Exception {
        AtomicInteger status = new AtomicInteger(200);
        healthServer = HttpServer.create(new InetSocketAddress(0), 0);
        healthServer.createContext("/health", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        healthServer.start();

        String url = "http://localhost:" + healthServer.getAddress().getPort();
        ClusterManager manager = new ClusterManager(50, 120, "logs");
        manager.registerInstance("TestService", "svc-1", url);
        try {
            Object instance = getManagedInstance(manager, "svc-1");
            invokeCheckInstance(manager, instance);
            assertEquals("HEALTHY", manager.getStatus().get("svc-1").get("status"));

            status.set(500);
            invokeCheckInstance(manager, instance);
            assertEquals("SUSPECTED", manager.getStatus().get("svc-1").get("status"));

            Thread.sleep(150);
            invokeCheckInstance(manager, instance);
            assertEquals("FAILED", manager.getStatus().get("svc-1").get("status"));

            Map<String, Object> entry = manager.getStatus().get("svc-1");
            assertEquals("TestService", entry.get("service"));
            assertEquals(Boolean.FALSE, entry.get("autoRestart"));
        } finally {
            if (healthServer != null) {
                healthServer.stop(0);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object getManagedInstance(ClusterManager manager, String instanceId) throws Exception {
        Field field = ClusterManager.class.getDeclaredField("instances");
        field.setAccessible(true);
        Map<String, Object> instances = (Map<String, Object>) field.get(manager);
        return instances.get(instanceId);
    }

    private static void invokeCheckInstance(ClusterManager manager, Object instance) throws Exception {
        Method method = ClusterManager.class.getDeclaredMethod("checkInstance", instance.getClass());
        method.setAccessible(true);
        method.invoke(manager, instance);
    }
}
