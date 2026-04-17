package com.securenet.storage;

import com.securenet.storage.impl.StorageServiceImpl;
import com.securenet.storage.server.StorageServiceServer;

public class StorageMain {
    public static void main(String[] args) throws Exception {
        int port = 9000;
        String host = "0.0.0.0";
        String jdbcUrl = "jdbc:postgresql://localhost:5432/securenet";
        String dbUser = System.getProperty("user.name");
        String dbPass = "";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--jdbc-url" -> jdbcUrl = args[++i];
                case "--db-user" -> dbUser = args[++i];
                case "--db-pass" -> dbPass = args[++i];
            }
        }

        StorageServiceImpl storage = new StorageServiceImpl(jdbcUrl, dbUser, dbPass);
        StorageServiceServer server = new StorageServiceServer(host, port, storage);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
