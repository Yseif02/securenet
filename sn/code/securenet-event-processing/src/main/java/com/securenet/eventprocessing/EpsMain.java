package com.securenet.eventprocessing;

import com.securenet.common.LoadBalancer;
import com.securenet.eventprocessing.impl.EventProcessingServiceImpl;
import com.securenet.eventprocessing.raft.RaftNode;
import com.securenet.eventprocessing.raft.RaftRpcServer;
import com.securenet.eventprocessing.server.EventProcessingServer;
import com.securenet.storage.StorageGateway;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Standalone entry point for a single EPS Raft node.
 *
 * <p>Each EPS node runs two HTTP servers:
 * <ul>
 *   <li>EPS API server — handles event ingestion and queries from IDFS/Gateway</li>
 *   <li>Raft RPC server — handles RequestVote and AppendEntries from peers</li>
 * </ul>
 */
public class EpsMain {

    private static final Logger log = Logger.getLogger(EpsMain.class.getName());

    public static void main(String[] args) throws Exception {
        String nodeId          = "eps-1";
        int    apiPort         = 9003;
        int    raftPort        = 9013;
        String storageUrl      = "http://localhost:9000";
        String notificationUrl = null;
        String dmsUrls = null;
        String epsApiUrls = "eps-1=http://localhost:9003,eps-2=http://localhost:9103,eps-3=http://localhost:9203";
        List<String> peers     = new ArrayList<>();
        String bindHost        = "0.0.0.0";
        String clusterManagerUrl = "http://localhost:9090";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--node-id"          -> nodeId          = args[++i];
                case "--api-port"         -> apiPort         = Integer.parseInt(args[++i]);
                case "--raft-port"        -> raftPort        = Integer.parseInt(args[++i]);
                case "--storage-url"      -> storageUrl      = args[++i];
                case "--notification-url" -> notificationUrl = args[++i];
                case "--dms-urls" -> dmsUrls = args[++i];
                case "--eps-api-urls" -> epsApiUrls = args[++i];
                case "--cluster-manager-url" -> clusterManagerUrl = args[++i];
                case "--peers"            -> {
                    for (String peer : args[++i].split(",")) {
                        String trimmed = peer.trim();
                        if (!trimmed.isEmpty()) peers.add(trimmed);
                    }
                }
                case "--host"             -> bindHost        = args[++i];
                default -> log.warning("Unknown argument: " + args[i]);
            }
        }

        LoadBalancer dmsLoadBalancer = new LoadBalancer("DMS", Arrays.asList(dmsUrls.split(",")));
        dmsLoadBalancer.watchClusterManager(clusterManagerUrl, "DMS");
        dmsLoadBalancer.start();


        log.info("=== EPS Node: " + nodeId + " ===");
        log.info("  API port:     " + apiPort);
        log.info("  Raft port:    " + raftPort);
        log.info("  Storage:      " + storageUrl);
        log.info("  DMS URL:      " + dmsUrls);
        log.info("  EPS API URLs: " + epsApiUrls);
        log.info("  Peers:        " + peers);

        StorageGateway storageGateway = new StorageGateway(storageUrl, clusterManagerUrl);

        EventProcessingServiceImpl epsService = new EventProcessingServiceImpl(
                storageGateway, notificationUrl, nodeId, dmsLoadBalancer);

        RaftNode raftNode = new RaftNode(nodeId, peers, epsService::onRaftCommit);
        epsService.setRaftNode(raftNode);

        RaftRpcServer raftRpcServer = new RaftRpcServer(bindHost, raftPort, raftNode);
        raftRpcServer.start();

        EventProcessingServer epsServer = new EventProcessingServer(
                bindHost, apiPort, epsService, parseNodeUrlMap(epsApiUrls));
        epsServer.start();

        raftNode.start();

        final String shutdownNodeId = nodeId;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("=== Shutting down EPS node " + shutdownNodeId + " ===");
            raftNode.stop();
            epsServer.stop();
            raftRpcServer.stop();
        }));

        log.info("[EPS:" + nodeId + "] Ready");
        Thread.currentThread().join();
    }

    private static Map<String, String> parseNodeUrlMap(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                result.put(parts[0].trim(), parts[1].trim());
            }
        }
        return result;
    }
}
