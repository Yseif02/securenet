package com.securenet.devicemanagement;

import com.securenet.devicemanagement.impl.DeviceManagementServiceImpl;
import com.securenet.devicemanagement.server.DeviceManagementServer;
import com.securenet.storage.StorageGateway;

public class DmsMain {
    public static void main(String[] args) throws Exception {
        int port = 9002;
        String host = "0.0.0.0";
        String storageUrl = "http://localhost:9000";
        String idfsUrl = "http://localhost:8080";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--storage-url" -> storageUrl = args[++i];
                case "--idfs-url" -> idfsUrl = args[++i];
            }
        }

        StorageGateway gateway = new StorageGateway(storageUrl);
        DeviceManagementServiceImpl service = new DeviceManagementServiceImpl(gateway, idfsUrl);
        DeviceManagementServer server = new DeviceManagementServer(host, port, service);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
