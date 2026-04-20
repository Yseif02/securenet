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
 * (DS Problem #4: Cluster Manager and Failure Detection). Automatically
 * restarts failed instances via per-service shell scripts on new ports.
 *
 * <h3>Usage</h3>
 * <pre>
 * java -cp ... com.securenet.common.ClusterManagerMain \
 *     --port 9090 \
 *     --scripts-dir /path/to/scripts/restart \
 *     --log-dir /path/to/logs/run_... \
 *     --instance UMS:ums-1:http://localhost:9001:restart-ums.sh \
 *     --instance UMS:ums-2:http://localhost:9011:restart-ums.sh \
 *     --instance EPS:eps-1:http://localhost:9003 \
 *     ...
 * </pre>
 *
 * <p>Instance format: {@code serviceName:instanceId:url} (no auto-restart)
 * or {@code serviceName:instanceId:url:restartScript} where restartScript
 * is a filename relative to {@code --scripts-dir}.
 */
public class ClusterManagerMain {

    private static final Logger log = Logger.getLogger(ClusterManagerMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9090;
        String host           = "0.0.0.0";
        long checkInterval    = 5000;
        long failureThreshold = 15000;
        String scriptsDir     = null;
        String logDir         = System.getProperty("java.io.tmpdir");
        List<String[]> instanceDefs = new ArrayList<>();
        long initialDelay = checkInterval * 3; // default


        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--check-interval" -> checkInterval = Long.parseLong(args[++i]);
                case "--failure-threshold" -> failureThreshold = Long.parseLong(args[++i]);
                case "--scripts-dir" -> scriptsDir = args[++i];
                case "--log-dir" -> logDir = args[++i];
                case "--initial-delay" -> initialDelay = Long.parseLong(args[++i]);
                case "--instance" -> {
                    String arg = args[++i];
                    // Split on | to separate url from optional restart script
                    String[] pipeparts = arg.split("\\|", 2);
                    String[] colonparts = pipeparts[0].split(":", 3);
                    if (colonparts.length < 3) {
                        System.err.println("Invalid instance format: " + arg);
                        System.exit(1);
                    }
                    // colonparts: [serviceName, instanceId, url]
                    // pipeparts[1] (optional): restartScript filename
                    String[] def = new String[pipeparts.length == 2 ? 4 : 3];
                    def[0] = colonparts[0];
                    def[1] = colonparts[1];
                    def[2] = colonparts[2];
                    if (pipeparts.length == 2) def[3] = pipeparts[1];
                    instanceDefs.add(def);
                }
            }
        }

        log.info("=== SecureNet Cluster Manager ===");
        log.info("  HTTP port:          " + port);
        log.info("  Check interval:     " + checkInterval + "ms");
        log.info("  Failure threshold:  " + failureThreshold + "ms");
        log.info("  Scripts dir:        " + scriptsDir);
        log.info("  Log dir:            " + logDir);
        log.info("  Instances:          " + instanceDefs.size());

        ClusterManager manager = new ClusterManager(checkInterval, failureThreshold, logDir);

        for (String[] def : instanceDefs) {
            String serviceName = def[0];
            String instanceId  = def[1];
            String url         = def[2];
            String restartScript = null;

            if (def.length == 4 && scriptsDir != null) {
                // Resolve script path relative to scripts dir
                restartScript = scriptsDir + "/" + def[3];
                // Make sure it's executable
                new java.io.File(restartScript).setExecutable(true);
            }

            manager.registerInstance(serviceName, instanceId, url, restartScript);
        }

        manager.start(initialDelay);

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