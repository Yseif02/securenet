package com.securenet.videostreaming;

import com.securenet.storage.StorageGateway;
import com.securenet.videostreaming.impl.VideoStreamingServiceImpl;
import com.securenet.videostreaming.server.VideoStreamingServer;

import java.util.logging.Logger;

public class VssMain {

    private static final Logger log = Logger.getLogger(VssMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9005;
        String host = "0.0.0.0";
        String storageUrl = "http://localhost:9000";
        String selfUrl = null;
        String clusterManagerUrl = "http://localhost:9090";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"                -> port             = Integer.parseInt(args[++i]);
                case "--host"                -> host              = args[++i];
                case "--storage-url"         -> storageUrl       = args[++i];
                case "--self-url"            -> selfUrl           = args[++i];
                case "--cluster-manager-url" -> clusterManagerUrl = args[++i];
            }
        }

        log.info("=== SecureNet Video Streaming Service ===");
        log.info("  Host:         " + host);
        log.info("  Port:         " + port);
        log.info("  Storage URL:  " + storageUrl);
        log.info("  Self URL:  " + selfUrl);

        StorageGateway gateway = new StorageGateway(storageUrl, clusterManagerUrl);
        VideoStreamingServiceImpl service = new VideoStreamingServiceImpl(gateway);
        VideoStreamingServer server = new VideoStreamingServer(host, port, service, selfUrl);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[VSS] Shutdown signal received");
            server.stop();
        }));

        log.info("[VSS] Ready — listening on " + host + ":" + port);
        Thread.currentThread().join();
    }
}