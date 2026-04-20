package com.securenet.eventprocessing;

import com.securenet.eventprocessing.impl.EventProcessingServiceImpl;
import com.securenet.eventprocessing.raft.RaftNode;
import com.securenet.eventprocessing.raft.RaftRpcServer;
import com.securenet.eventprocessing.server.EventProcessingServer;
import com.securenet.storage.StorageGateway;

import java.util.ArrayList;
import java.util.List;
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
        String dmsUrl = null;
        List<String> peers     = new ArrayList<>();
        String bindHost        = "0.0.0.0";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--node-id"          -> nodeId          = args[++i];
                case "--api-port"         -> apiPort         = Integer.parseInt(args[++i]);
                case "--raft-port"        -> raftPort        = Integer.parseInt(args[++i]);
                case "--storage-url"      -> storageUrl      = args[++i];
                case "--notification-url" -> notificationUrl = args[++i];
                case "--dms-url" -> dmsUrl = args[++i];
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

        log.info("=== EPS Node: " + nodeId + " ===");
        log.info("  API port:     " + apiPort);
        log.info("  Raft port:    " + raftPort);
        log.info("  Storage:      " + storageUrl);
        log.info("  DMS URL:      " + dmsUrl);
        log.info("  Peers:        " + peers);

        StorageGateway storageGateway = new StorageGateway(storageUrl);

        EventProcessingServiceImpl epsService = new EventProcessingServiceImpl(
            storageGateway, notificationUrl, nodeId, dmsUrl);

        RaftNode raftNode = new RaftNode(nodeId, peers, epsService::onRaftCommit);
        epsService.setRaftNode(raftNode);

        RaftRpcServer raftRpcServer = new RaftRpcServer(bindHost, raftPort, raftNode);
        raftRpcServer.start();

        EventProcessingServer epsServer = new EventProcessingServer(bindHost, apiPort, epsService);
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
}