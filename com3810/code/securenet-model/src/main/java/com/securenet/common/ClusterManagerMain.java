package com.securenet.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Standalone process for the SecureNet Cluster Manager.
 *
 * <p>Monitors all registered service instances via periodic health checks
 * (DS Problem #4: Cluster Manager and Failure Detection).
 */
public class ClusterManagerMain {

    private static final Logger log = Logger.getLogger(ClusterManagerMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9090;
        String host = "0.0.0.0";
        long checkInterval    = 5000;
        long failureThreshold = 15000;
        List<String[]> instanceDefs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"              -> port             = Integer.parseInt(args[++i]);
                case "--host"              -> host             = args[++i];
                case "--check-interval"    -> checkInterval    = Long.parseLong(args[++i]);
                case "--failure-threshold" -> failureThreshold = Long.parseLong(args[++i]);
                case "--instance" -> {
                    String[] parts = args[++i].split(":", 3);
                    if (parts.length != 3) {
                        System.err.println("Invalid instance format: " + args[i]
                                + " (expected serviceName:instanceId:url)");
                        System.exit(1);
                    }
                    instanceDefs.add(parts);
                }
                default -> log.warning("Unknown argument: " + args[i]);
            }
        }

        log.info("=== SecureNet Cluster Manager ===");
        log.info("  HTTP port:          " + port);
        log.info("  Check interval:     " + checkInterval + "ms");
        log.info("  Failure threshold:  " + failureThreshold + "ms");
        log.info("  Instances:          " + instanceDefs.size());

        ClusterManager manager = new ClusterManager(checkInterval, failureThreshold,
                (serviceName, instanceId, url) -> {
                    log.severe("[ClusterManager] FAILURE DETECTED: "
                            + serviceName + "/" + instanceId + " at " + url);
                    log.severe("[ClusterManager] A replacement should be started for "
                            + instanceId);
                });

        for (String[] def : instanceDefs) {
            manager.registerInstance(def[0], def[1], def[2]);
        }

        manager.start();

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(2));

        httpServer.createContext("/cluster/status", ex -> {
            String json  = JsonUtil.toJson(manager.getStatus());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });

        httpServer.createContext("/health", ex -> {
            byte[] bytes = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });

        httpServer.start();
        log.info("[ClusterManager] HTTP status endpoint on " + host + ":" + port);
        log.info("[ClusterManager] Check status: curl http://localhost:" + port
                + "/cluster/status");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            manager.stop();
            httpServer.stop(0);
            System.out.println("[ClusterManager] Shut down");
        }));

        Thread.currentThread().join();
    }
}