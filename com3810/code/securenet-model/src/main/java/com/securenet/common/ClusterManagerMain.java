package com.securenet.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Standalone process for the SecureNet Cluster Manager.
 *
 * <p>Monitors all registered service instances via periodic health checks
 * (DS Problem #4: Cluster Manager and Failure Detection). Exposes an HTTP
 * endpoint at {@code /cluster/status} for inspecting instance health.
 *
 * <p>Instances are registered via {@code --instance} CLI arguments.
 * When a service is declared FAILED, the manager logs the failure and
 * optionally executes a restart command.
 *
 * <h3>Usage</h3>
 * <pre>
 * java -cp ... com.securenet.common.ClusterManagerMain \
 *     --port 9090 \
 *     --instance UMS:ums-1:http://localhost:9001 \
 *     --instance UMS:ums-2:http://localhost:9011 \
 *     --instance DMS:dms-1:http://localhost:9002 \
 *     ...
 * </pre>
 *
 * <p>Instance format: {@code serviceName:instanceId:url}
 */
public class ClusterManagerMain {

    public static void main(String[] args) throws Exception {
        int port = 9090;
        String host = "0.0.0.0";
        long checkInterval = 5000;
        long failureThreshold = 15000;
        List<String[]> instanceDefs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--check-interval" -> checkInterval = Long.parseLong(args[++i]);
                case "--failure-threshold" -> failureThreshold = Long.parseLong(args[++i]);
                case "--instance" -> {
                    String[] parts = args[++i].split(":", 3);
                    if (parts.length != 3) {
                        System.err.println("Invalid instance format: " + args[i] +
                                " (expected serviceName:instanceId:url)");
                        System.exit(1);
                    }
                    instanceDefs.add(parts);
                }
                default -> System.err.println("Unknown argument: " + args[i]);
            }
        }

        System.out.println("=== SecureNet Cluster Manager ===");
        System.out.println("  HTTP port:          " + port);
        System.out.println("  Check interval:     " + checkInterval + "ms");
        System.out.println("  Failure threshold:  " + failureThreshold + "ms");
        System.out.println("  Instances:          " + instanceDefs.size());
        System.out.println();

        // Create cluster manager with failure callback
        ClusterManager manager = new ClusterManager(checkInterval, failureThreshold,
                (serviceName, instanceId, url) -> {
                    System.err.println("[ClusterManager] FAILURE DETECTED: " +
                            serviceName + "/" + instanceId + " at " + url);
                    System.err.println("[ClusterManager] A replacement should be started.");
                    // In production: trigger process restart via shell command
                    // or container orchestrator API
                });

        // Register all instances
        for (String[] def : instanceDefs) {
            manager.registerInstance(def[0], def[1], def[2]);
        }

        // Start health check loop
        manager.start();

        // Start HTTP server for status inspection
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(2));

        httpServer.createContext("/cluster/status", ex -> {
            String json = JsonUtil.toJson(manager.getStatus());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });

        httpServer.createContext("/health", ex -> {
            byte[] bytes = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });

        httpServer.start();
        System.out.println("[ClusterManager] HTTP status endpoint on " + host + ":" + port);
        System.out.println("[ClusterManager] Check status: curl http://localhost:" + port + "/cluster/status");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            manager.stop();
            httpServer.stop(0);
            System.out.println("[ClusterManager] Shut down");
        }));

        Thread.currentThread().join();
    }
}
