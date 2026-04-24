package com.securenet.devicemanagement;

import com.securenet.common.LoadBalancer;
import com.securenet.devicemanagement.impl.DeviceManagementServiceImpl;
import com.securenet.devicemanagement.server.DeviceManagementServer;
import com.securenet.storage.StorageGateway;

import java.util.Arrays;
import java.util.logging.Logger;

public class DmsMain {

    private static final Logger log = Logger.getLogger(DmsMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9002;
        String host = "0.0.0.0";
        String storageUrl = "http://localhost:9000";
        String idfsUrl = "http://localhost:8080";
        String vssUrls = "http://localhost:9005,http://localhost:9015,http://localhost:9025";
        String clusterManagerUrl = "http://localhost:9090";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"                -> port             = Integer.parseInt(args[++i]);
                case "--host"                -> host              = args[++i];
                case "--storage-url"         -> storageUrl       = args[++i];
                case "--idfs-url"            -> idfsUrl           = args[++i];
                case "--vss-urls"            -> vssUrls           = args[++i];
                case "--cluster-manager-url" -> clusterManagerUrl = args[++i];
            }
        }

        log.info("=== SecureNet Device Management Service ===");
        log.info("  Host:         " + host);
        log.info("  Port:         " + port);
        log.info("  Storage URL:  " + storageUrl);
        log.info("  IDFS URL:     " + idfsUrl);
        log.info("  VSS URLs:     " + vssUrls);
        log.info("  Cluster Manager Url:     " + clusterManagerUrl);

        StorageGateway gateway = new StorageGateway(storageUrl, clusterManagerUrl);
        LoadBalancer idfsLoadBalancer = new LoadBalancer("IDFS", Arrays.asList(idfsUrl.split(",")));
        idfsLoadBalancer.watchClusterManager(clusterManagerUrl, "IDFS");
        idfsLoadBalancer.start();
        LoadBalancer vssLoadBalancer = new LoadBalancer("VSS", Arrays.asList(vssUrls.split(",")));
        vssLoadBalancer.watchClusterManager(clusterManagerUrl, "VSS");
        vssLoadBalancer.start();
        DeviceManagementServiceImpl service = new DeviceManagementServiceImpl(
                gateway, idfsLoadBalancer, vssLoadBalancer);
        DeviceManagementServer server = new DeviceManagementServer(host, port, service);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[DMS] Shutdown signal received");
            server.stop();
        }));

        log.info("[DMS] Ready — listening on " + host + ":" + port);
        Thread.currentThread().join();
    }
}