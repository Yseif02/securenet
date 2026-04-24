package com.securenet.iotfirmware.mqtt;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Standalone MQTT broker process.
 *
 * <p>Previously the Moquette broker was embedded inside idfs-1, making
 * it a single point of failure — if idfs-1 died, the broker died with it
 * and no device could communicate. This class extracts the broker into its
 * own JVM process so it can be monitored and restarted independently by
 * the ClusterManager.
 *
 * <p>All three IDFS instances connect to this broker as clients (using
 * Paho), exactly as devices do. IDFS instances are now fully symmetric —
 * any of them can be restarted without affecting the broker.
 *
 * <p>A lightweight HTTP server is started on {@code --http-port} (default 1884)
 * so the ClusterManager can health-check the broker via {@code GET /health}.
 * The MQTT port (default 1883) is registered with ClusterManager as
 * {@code http://localhost:1884} so the health endpoint is reachable.
 *
 * <p>Args:
 * <pre>
 *   --port       &lt;mqtt-port&gt;   (default 1883)
 *   --http-port  &lt;http-port&gt;   (default 1884)
 *   --host       &lt;host&gt;        (default 0.0.0.0)
 * </pre>
 */
public class MqttBrokerMain {

    private static final Logger log = Logger.getLogger(MqttBrokerMain.class.getName());

    public static void main(String[] args) throws Exception {
        String host  = "0.0.0.0";
        int mqttPort = 1883;
        int httpPort = 1884;   // health-check endpoint for ClusterManager

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"      -> mqttPort = Integer.parseInt(args[++i]);
                case "--http-port" -> httpPort  = Integer.parseInt(args[++i]);
                case "--host"      -> host      = args[++i];
            }
        }

        log.info("=== SecureNet Standalone MQTT Broker ===");
        log.info("  Host:      " + host);
        log.info("  MQTT port: " + mqttPort);
        log.info("  HTTP port: " + httpPort + "  (ClusterManager health endpoint)");

        EmbeddedMqttBroker broker = new EmbeddedMqttBroker(host, mqttPort);
        broker.start();

        // -----------------------------------------------------------------------
        // HTTP health endpoint — ClusterManager polls GET /health on this port
        // -----------------------------------------------------------------------
        HttpServer httpServer = HttpServer.create(
                new InetSocketAddress(host, httpPort), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(2));
        httpServer.createContext("/health", ex -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        httpServer.start();
        log.info("[MqttBroker] HTTP health endpoint on " + host + ":" + httpPort);

        log.info("[MqttBroker] Ready — MQTT on " + host + ":" + mqttPort
                + "  health on " + host + ":" + httpPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[MqttBroker] Shutdown signal received");
            broker.stop();
            httpServer.stop(0);
        }));

        Thread.currentThread().join();
    }
}