package com.securenet.usermanagement;

import com.securenet.storage.StorageGateway;
import com.securenet.usermanagement.impl.UserManagementServiceImpl;
import com.securenet.usermanagement.server.UserManagementServer;

import java.util.logging.Logger;

public class UmsMain {

    private static final Logger log = Logger.getLogger(UmsMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9001;
        String host = "0.0.0.0";
        String storageUrl = "http://localhost:9000";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"        -> port       = Integer.parseInt(args[++i]);
                case "--host"        -> host        = args[++i];
                case "--storage-url" -> storageUrl  = args[++i];
            }
        }

        log.info("=== SecureNet User Management Service ===");
        log.info("  Host:         " + host);
        log.info("  Port:         " + port);
        log.info("  Storage URL:  " + storageUrl);

        StorageGateway gateway = new StorageGateway(storageUrl);
        UserManagementServiceImpl service = new UserManagementServiceImpl(gateway);
        UserManagementServer server = new UserManagementServer(host, port, service);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[UMS] Shutdown signal received");
            server.stop();
        }));

        log.info("[UMS] Ready — listening on " + host + ":" + port);
        Thread.currentThread().join();
    }
}