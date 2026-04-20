package com.securenet.iotfirmware;

import com.securenet.common.LoadBalancer;
import com.securenet.iotfirmware.mqtt.EmbeddedMqttBroker;
import com.securenet.iotfirmware.server.IdfsServer;
import com.securenet.storage.StorageGateway;

import java.util.Arrays;
import java.util.logging.Logger;

public class IdfsMain {

    private static final Logger log = Logger.getLogger(IdfsMain.class.getName());

    public static void main(String[] args) throws Exception {
        int httpPort = 8080;
        int mqttPort = 1883;
        String host = "0.0.0.0";
        String dmsUrl = "http://localhost:9002,http://localhost:9012,http://localhost:9022";
        String epsUrl = "http://localhost:9003,http://localhost:9103,http://localhost:9203";
        String clusterManagerUrl = "http://localhost:9090";
        String storageUrl = "http://localhost:9000,http://localhost:9010,http://localhost:9020";
        String mqttBrokerUrl = "tcp://localhost:1883";
        boolean startBroker = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--http-port"       -> httpPort      = Integer.parseInt(args[++i]);
                case "--mqtt-port"       -> mqttPort      = Integer.parseInt(args[++i]);
                case "--host"            -> host          = args[++i];
                case "--dms-url"         -> dmsUrl        = args[++i];
                case "--eps-url"         -> epsUrl        = args[++i];
                case "--cluster-manager-url" -> clusterManagerUrl = args[++i];
                case "--mqtt-broker-url" -> mqttBrokerUrl = args[++i];
                case "--storage-url" -> storageUrl = args[++i];
                case "--no-broker"       -> startBroker   = false;
            }
        }

        log.info("=== SecureNet IDFS + MQTT Broker ===");
        log.info("  Host:            " + host);
        log.info("  HTTP port:       " + httpPort);
        log.info("  MQTT port:       " + mqttPort);
        log.info("  DMS URL:         " + dmsUrl);
        log.info("  EPS URL:         " + epsUrl);
        log.info("  MQTT broker URL: " + mqttBrokerUrl);
        log.info("  Start broker:    " + startBroker);

        LoadBalancer dmsLoadBalancer = new LoadBalancer("DMS", Arrays.asList(dmsUrl.split(",")));
        dmsLoadBalancer.watchClusterManager(clusterManagerUrl, "DMS");
        dmsLoadBalancer.start();

        LoadBalancer epsLoadBalancer = new LoadBalancer("EPS", Arrays.asList(epsUrl.split(",")));
        epsLoadBalancer.watchClusterManager(clusterManagerUrl, "EPS");
        epsLoadBalancer.start();

        EmbeddedMqttBroker broker = null;
        if (startBroker) {
            broker = new EmbeddedMqttBroker(host, mqttPort);
            broker.start();
            log.info("[IDFS] Embedded MQTT broker started on port " + mqttPort);
            Thread.sleep(500);
        }

        StorageGateway storageGateway = new StorageGateway(storageUrl);
        IdfsServer idfs = new IdfsServer(host, httpPort, dmsLoadBalancer, epsLoadBalancer, mqttBrokerUrl, storageGateway);
        idfs.start();

        final EmbeddedMqttBroker brokerRef = broker;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[IDFS] Shutdown signal received");
            idfs.stop();
            if (brokerRef != null) brokerRef.stop();
        }));

        log.info("[IDFS] Ready — HTTP on " + host + ":" + httpPort
                + ", MQTT on " + mqttBrokerUrl);
        Thread.currentThread().join();
    }
}