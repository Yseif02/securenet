package com.securenet.usermanagement;

import com.securenet.storage.StorageGateway;
import com.securenet.usermanagement.impl.UserManagementServiceImpl;
import com.securenet.usermanagement.server.UserManagementServer;

public class UmsMain {
    public static void main(String[] args) throws Exception {
        int port = 9001;
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
        UserManagementServiceImpl service = new UserManagementServiceImpl(gateway);
        UserManagementServer server = new UserManagementServer(host, port, service);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
