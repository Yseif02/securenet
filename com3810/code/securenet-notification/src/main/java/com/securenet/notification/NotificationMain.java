package com.securenet.notification;

import com.securenet.notification.impl.NotificationServiceImpl;
import com.securenet.notification.server.NotificationServer;
import com.securenet.storage.StorageGateway;

import java.util.logging.Logger;

public class NotificationMain {

    private static final Logger log = Logger.getLogger(NotificationMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9004;
        String host = "0.0.0.0";
        String storageUrl = "http://localhost:9000";
        String clusterManagerUrl = "http://localhost:9090";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"                -> port             = Integer.parseInt(args[++i]);
                case "--host"                -> host              = args[++i];
                case "--storage-url"         -> storageUrl       = args[++i];
                case "--cluster-manager-url" -> clusterManagerUrl = args[++i];
            }
        }

        log.info("=== SecureNet Notification Service ===");
        log.info("  Host:         " + host);
        log.info("  Port:         " + port);
        log.info("  Storage URL:  " + storageUrl);

        StorageGateway gateway = new StorageGateway(storageUrl, clusterManagerUrl);
        NotificationServiceImpl service = new NotificationServiceImpl(gateway);
        NotificationServer server = new NotificationServer(host, port, service);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Notification] Shutdown signal received");
            server.stop();
        }));

        log.info("[Notification] Ready — listening on " + host + ":" + port);
        Thread.currentThread().join();
    }
}