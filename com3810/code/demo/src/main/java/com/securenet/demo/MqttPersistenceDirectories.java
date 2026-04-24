package com.securenet.demo;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

final class MqttPersistenceDirectories {

    private MqttPersistenceDirectories() {
    }

    static MqttClient createMockDeviceClient(String brokerUrl, String clientId) throws MqttException {
        return new MqttClient(brokerUrl, clientId, new MemoryPersistence());
    }
}
