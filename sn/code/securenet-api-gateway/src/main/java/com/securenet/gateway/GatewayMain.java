package com.securenet.gateway;

import com.securenet.common.LoadBalancer;
import com.securenet.gateway.impl.APIGatewayServiceImpl;
import com.securenet.gateway.server.APIGatewayServer;

import java.util.*;
import java.util.logging.Logger;

public class GatewayMain {

    private static final Logger log = Logger.getLogger(GatewayMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 443;
        String host = "0.0.0.0";
        String umsUrls = "http://localhost:9001";
        String dmsUrls = "http://localhost:9002";
        String epsUrls = "http://localhost:9003";
        String notifyUrls = "http://localhost:9004";
        String vssUrls = "http://localhost:9005";
        String clusterManagerUrl = "http://localhost:9090";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port"        -> port       = Integer.parseInt(args[++i]);
                case "--host"        -> host        = args[++i];
                case "--ums-urls"    -> umsUrls     = args[++i];
                case "--dms-urls"    -> dmsUrls     = args[++i];
                case "--eps-urls"    -> epsUrls     = args[++i];
                case "--notify-urls" -> notifyUrls  = args[++i];
                case "--vss-urls"    -> vssUrls     = args[++i];
                case "--cluster-manager-url" -> clusterManagerUrl = args[++i];
            }
        }

        log.info("=== SecureNet API Gateway ===");
        log.info("  Host:         " + host);
        log.info("  Port:         " + port);
        log.info("  UMS URLs:     " + umsUrls);
        log.info("  DMS URLs:     " + dmsUrls);
        log.info("  EPS URLs:     " + epsUrls);
        log.info("  Notify URLs:  " + notifyUrls);
        log.info("  VSS URLs:     " + vssUrls);
        log.info("  Cluster Manager Url:     " + clusterManagerUrl);

        LoadBalancer umsLb = new LoadBalancer("UMS", Arrays.asList(umsUrls.split(",")));
        umsLb.watchClusterManager(clusterManagerUrl, "UMS");
        umsLb.start();
        log.info("[APIGateway] UMS load balancer started: " + umsUrls);

        Map<String, LoadBalancer> serviceLbs = new HashMap<>();
        LoadBalancer dmsLoadBalancer = createLb("DMS", dmsUrls, clusterManagerUrl);
        serviceLbs.put("device-management", dmsLoadBalancer);
        LoadBalancer epsLoadBalancer = createLb("EPS", epsUrls, clusterManagerUrl);
        serviceLbs.put("event-processing", epsLoadBalancer);
        LoadBalancer notifLoadBalancer = createLb("Notification", notifyUrls, clusterManagerUrl);
        serviceLbs.put("notification", notifLoadBalancer);
        LoadBalancer vssLoadBalancer = createLb("VSS", vssUrls, clusterManagerUrl);
        serviceLbs.put("video-streaming", vssLoadBalancer);
        log.info("[APIGateway] Service load balancers started: " + serviceLbs.keySet());

        APIGatewayServiceImpl gateway = new APIGatewayServiceImpl(umsLb, serviceLbs);
        APIGatewayServer server = new APIGatewayServer(host, port, gateway);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[APIGateway] Shutdown signal received");
            server.stop();
            umsLb.stop();
            serviceLbs.values().forEach(LoadBalancer::stop);
            System.out.println("[APIGateway] All load balancers stopped");
        }));

        System.out.println("[APIGateway] Ready — listening on " + host + ":" + port);
        Thread.currentThread().join();
    }

    private static LoadBalancer createLb(String name, String urls, String clusterManagerUrl) {
        LoadBalancer lb = new LoadBalancer(name, Arrays.asList(urls.split(",")));
        lb.watchClusterManager(clusterManagerUrl, name);        lb.start();
        log.info("[APIGateway] Load balancer created: " + name + " -> " + urls);
        return lb;
    }
}