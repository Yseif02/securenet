package com.securenet.notification;

import com.securenet.notification.impl.NotificationServiceImpl;
import com.securenet.notification.server.NotificationServer;
import com.securenet.storage.StorageGateway;

public class NotificationMain {
    public static void main(String[] args) throws Exception {
        int port = 9004;
        String host = "0.0.0.0";
        String storageUrl = "http://localhost:9000";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--storage-url" -> storageUrl = args[++i];
            }
        }

        StorageGateway gateway = new StorageGateway(storageUrl);
        NotificationServiceImpl service = new NotificationServiceImpl(gateway);
        NotificationServer server = new NotificationServer(host, port, service);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
