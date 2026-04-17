package com.securenet.iotfirmware.mqtt;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;

import java.io.IOException;
import java.util.Properties;

/**
 * Embedded MQTT broker backed by Moquette.
 *
 * <p>Runs inside the SecureNet orchestrator process (or as a standalone
 * process) and accepts device MQTT connections for command dispatch,
 * event publishing, and heartbeat channels.
 *
 * <p>IDFS subscribes to device event topics via a shared subscription
 * ({@code $share/idfs-cluster/securenet/devices/+/events/#}) and
 * forwards them to the Event Processing Service.
 *
 * <p>The broker listens on the configured TCP port (default 1883) with
 * no TLS for development. Production deployments should enable TLS on
 * port 8883 and configure client certificate authentication.
 */
public class EmbeddedMqttBroker {

    private final String host;
    private final int port;
    private Server broker;

    /**
     * @param host bind address (e.g. "0.0.0.0")
     * @param port MQTT TCP port (default 1883)
     */
    public EmbeddedMqttBroker(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Starts the embedded MQTT broker.
     *
     * <p>Configures Moquette with in-memory persistence and anonymous
     * access for development. The broker supports MQTT 3.1.1 shared
     * subscriptions which IDFS uses for load-balanced event ingestion.
     *
     * <p>Before binding, checks whether the port is already in use. If it
     * is, prints a diagnostic message suggesting how to free it.
     *
     * @throws IOException if the broker fails to bind to the port
     */
    public void start() throws IOException {
        if (!isPortAvailable(host, port)) {
            System.err.println("[MqttBroker] ERROR: Port " + port + " is already in use.");
            System.err.println("  Another MQTT broker (Mosquitto?) or a previous run may still be bound.");
            System.err.println("  On macOS/Linux: lsof -i :" + port + "  then  kill <PID>");
            throw new IOException("Port " + port + " already in use");
        }

        Properties props = new Properties();
        props.setProperty("host", host);
        props.setProperty("port", String.valueOf(port));
        props.setProperty("allow_anonymous", "true");
        props.setProperty("persistence_store", "");

        IConfig config = new MemoryConfig(props);
        broker = new Server();
        broker.startServer(config);

        System.out.println("[MqttBroker] started on " + host + ":" + port);
    }

    private static boolean isPortAvailable(String host, int port) {
        try (var ss = new java.net.ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new java.net.InetSocketAddress(host, port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Stops the embedded MQTT broker, disconnecting all clients.
     */
    public void stop() {
        if (broker != null) {
            broker.stopServer();
            System.out.println("[MqttBroker] stopped");
        }
    }
}
