package com.securenet.eventprocessing;

import com.securenet.eventprocessing.impl.EventProcessingServiceImpl;
import com.securenet.eventprocessing.raft.RaftNode;
import com.securenet.eventprocessing.raft.RaftRpcServer;
import com.securenet.eventprocessing.server.EventProcessingServer;
import com.securenet.storage.StorageGateway;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone entry point for a single EPS Raft node.
 *
 * <p>Each EPS node runs two HTTP servers:
 * <ul>
 *   <li>EPS API server — handles event ingestion and queries from IDFS/Gateway</li>
 *   <li>Raft RPC server — handles RequestVote and AppendEntries from peers</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * java -cp ... com.securenet.eventprocessing.EpsMain \
 *     --node-id eps-1 \
 *     --api-port 9003 \
 *     --raft-port 9013 \
 *     --storage-url http://localhost:9000 \
 *     --peers http://localhost:9023,http://localhost:9033
 * </pre>
 *
 * <p>For a 3-node cluster, start three instances with different ports
 * and each listing the other two as peers.
 */
public class EpsMain {

    public static void main(String[] args) throws Exception {
        // Default values
        String nodeId = "eps-1";
        int apiPort = 9003;
        int raftPort = 9013;
        String storageUrl = "http://localhost:9000";
        String notificationUrl = null;
        List<String> peers = new ArrayList<>();
        String bindHost = "0.0.0.0";

        // Parse CLI args
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--node-id" -> nodeId = args[++i];
                case "--api-port" -> apiPort = Integer.parseInt(args[++i]);
                case "--raft-port" -> raftPort = Integer.parseInt(args[++i]);
                case "--storage-url" -> storageUrl = args[++i];
                case "--notification-url" -> notificationUrl = args[++i];
                case "--peers" -> {
                    for (String peer : args[++i].split(",")) {
                        String trimmed = peer.trim();
                        if (!trimmed.isEmpty()) peers.add(trimmed);
                    }
                }
                case "--host" -> bindHost = args[++i];
                default -> System.err.println("Unknown argument: " + args[i]);
            }
        }

        System.out.println("=== EPS Node: " + nodeId + " ===");
        System.out.println("  API port:     " + apiPort);
        System.out.println("  Raft port:    " + raftPort);
        System.out.println("  Storage:      " + storageUrl);
        System.out.println("  Peers:        " + peers);
        System.out.println();

        // Create storage gateway
        StorageGateway storageGateway = new StorageGateway(storageUrl);

        // Create EPS service
        EventProcessingServiceImpl epsService = new EventProcessingServiceImpl(
                storageGateway, notificationUrl);

        // Create Raft node with commit callback
        RaftNode raftNode = new RaftNode(nodeId, peers, epsService::onRaftCommit);
        epsService.setRaftNode(raftNode);

        // Start Raft RPC server (peers communicate on this port)
        RaftRpcServer raftRpcServer = new RaftRpcServer(bindHost, raftPort, raftNode);
        raftRpcServer.start();

        // Start EPS API server (IDFS/clients send events to this port)
        EventProcessingServer epsServer = new EventProcessingServer(bindHost, apiPort, epsService);
        epsServer.start();

        // Start Raft election/heartbeat timers
        raftNode.start();

        // Shutdown hook
        final String shutdownNodeId = nodeId;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("=== Shutting down EPS node " + shutdownNodeId + " ===");
            raftNode.stop();
            epsServer.stop();
            raftRpcServer.stop();
        }));

        System.out.println("[EPS:" + nodeId + "] Ready");
        Thread.currentThread().join();
    }
}
