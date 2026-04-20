package com.securenet.storage;

import com.securenet.storage.impl.StorageServiceImpl;
import com.securenet.storage.server.StorageServiceServer;

import java.util.logging.Logger;

public class StorageMain {

    private static final Logger log = Logger.getLogger(StorageMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9000;
        String host = "0.0.0.0";
        String jdbcUrl = "jdbc:postgresql://localhost:5432/securenet";
        String dbUser = System.getProperty("user.name");
        String dbPass = "";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"      -> port    = Integer.parseInt(args[++i]);
                case "--host"      -> host    = args[++i];
                case "--jdbc-url"  -> jdbcUrl = args[++i];
                case "--db-user"   -> dbUser  = args[++i];
                case "--db-pass"   -> dbPass  = args[++i];
            }
        }

        log.info("=== SecureNet Storage Service ===");
        log.info("  Host:      " + host);
        log.info("  Port:      " + port);
        log.info("  JDBC URL:  " + jdbcUrl);
        log.info("  DB User:   " + dbUser);

        log.info("[Storage] Connecting to PostgreSQL at " + jdbcUrl);
        StorageServiceImpl storage = new StorageServiceImpl(jdbcUrl, dbUser, dbPass);
        log.info("[Storage] PostgreSQL connected and schema initialized");

        StorageServiceServer server = new StorageServiceServer(host, port, storage);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Storage] Shutdown signal received");
            server.stop();
        }));

        log.info("[Storage] Ready — listening on " + host + ":" + port);
        Thread.currentThread().join();
    }
}