package com.securenet.iotfirmware.mqtt;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Embedded MQTT broker backed by Moquette.
 *
 * <p>Runs inside the IDFS process and accepts device MQTT connections
 * for command dispatch, event publishing, and heartbeat channels.
 */
public class EmbeddedMqttBroker {

    private static final Logger log = Logger.getLogger(EmbeddedMqttBroker.class.getName());

    private final String host;
    private final int port;
    private Server broker;

    public EmbeddedMqttBroker(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        log.info("[MqttBroker] Starting on " + host + ":" + port);

        if (!isPortAvailable(host, port)) {
            log.severe("[MqttBroker] Port " + port + " is already in use."
                    + " Another MQTT broker or previous run may still be bound."
                    + " On macOS/Linux: lsof -i :" + port + "  then  kill <PID>");
            throw new IOException("Port " + port + " already in use");
        }

        Properties props = new Properties();
        props.setProperty("host",              host);
        props.setProperty("port",              String.valueOf(port));
        props.setProperty("allow_anonymous",   "true");
        props.setProperty("persistence_store", "");

        IConfig config = new MemoryConfig(props);
        broker = new Server();
        broker.startServer(config);

        log.info("[MqttBroker] started on " + host + ":" + port);
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

    public void stop() {
        if (broker != null) {
            broker.stopServer();
            System.out.println("[MqttBroker] stopped");
        }
    }
}