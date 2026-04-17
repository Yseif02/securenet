package com.securenet.videostreaming;

import com.securenet.storage.StorageGateway;
import com.securenet.videostreaming.impl.VideoStreamingServiceImpl;
import com.securenet.videostreaming.server.VideoStreamingServer;

public class VssMain {
    public static void main(String[] args) throws Exception {
        int port = 9005;
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
        VideoStreamingServiceImpl service = new VideoStreamingServiceImpl(gateway);
        VideoStreamingServer server = new VideoStreamingServer(host, port, service);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
