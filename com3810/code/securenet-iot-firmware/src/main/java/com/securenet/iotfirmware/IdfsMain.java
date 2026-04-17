package com.securenet.iotfirmware;

import com.securenet.iotfirmware.mqtt.EmbeddedMqttBroker;
import com.securenet.iotfirmware.server.IdfsServer;

public class IdfsMain {
    public static void main(String[] args) throws Exception {
        int httpPort = 8080;
        int mqttPort = 1883;
        String host = "0.0.0.0";
        String dmsUrl = "http://localhost:9002";
        String epsUrl = "http://localhost:9003";
        String mqttBrokerUrl = "tcp://localhost:1883";
        boolean startBroker = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--http-port" -> httpPort = Integer.parseInt(args[++i]);
                case "--mqtt-port" -> mqttPort = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--dms-url" -> dmsUrl = args[++i];
                case "--eps-url" -> epsUrl = args[++i];
                case "--mqtt-broker-url" -> mqttBrokerUrl = args[++i];
                case "--no-broker" -> startBroker = false;
            }
        }

        EmbeddedMqttBroker broker = null;
        if (startBroker) {
            broker = new EmbeddedMqttBroker(host, mqttPort);
            broker.start();
            Thread.sleep(500);
        }

        IdfsServer idfs = new IdfsServer(host, httpPort, dmsUrl, epsUrl, mqttBrokerUrl);
        idfs.start();

        final EmbeddedMqttBroker brokerRef = broker;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            idfs.stop();
            if (brokerRef != null) brokerRef.stop();
        }));
        Thread.currentThread().join();
    }
}
