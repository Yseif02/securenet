package com.securenet.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Standalone process for the SecureNet Cluster Manager.
 *
 * <p>Monitors all registered service instances via periodic health checks
 * (DS Problem #4: Cluster Manager and Failure Detection). Automatically
 * restarts failed instances via per-service shell scripts on new ports.
 *
 * <h3>Usage</h3>
 * <pre>
 * java -cp ... com.securenet.common.ClusterManagerMain \
 *     --port 9090 \
 *     --scripts-dir /path/to/scripts/restart \
 *     --log-dir /path/to/logs/run_... \
 *     --instance UMS:ums-1:http://localhost:9001:restart-ums.sh \
 *     --instance UMS:ums-2:http://localhost:9011:restart-ums.sh \
 *     --instance EPS:eps-1:http://localhost:9003 \
 *     ...
 * </pre>
 *
 * <p>Instance format: {@code serviceName:instanceId:url} (no auto-restart)
 * or {@code serviceName:instanceId:url:restartScript} where restartScript
 * is a filename relative to {@code --scripts-dir}.
 */
public class ClusterManagerMain {

    private static final Logger log = Logger.getLogger(ClusterManagerMain.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 9090;
        String host           = "0.0.0.0";
        long checkInterval    = 5000;
        long failureThreshold = 15000;
        String scriptsDir     = null;
        String logDir         = System.getProperty("java.io.tmpdir");
        String nodeId         = null;
        String clusterPeers   = null;
        List<String[]> instanceDefs = new ArrayList<>();
        long initialDelay = checkInterval * 3; // default


        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--check-interval" -> checkInterval = Long.parseLong(args[++i]);
                case "--failure-threshold" -> failureThreshold = Long.parseLong(args[++i]);
                case "--scripts-dir" -> scriptsDir = args[++i];
                case "--log-dir" -> logDir = args[++i];
                case "--initial-delay" -> initialDelay = Long.parseLong(args[++i]);
                case "--node-id" -> nodeId = args[++i];
                case "--cluster-peers" -> clusterPeers = args[++i];
                case "--instance" -> {
                    String arg = args[++i];
                    // Split on | to separate url from optional restart script
                    String[] pipeparts = arg.split("\\|", 2);
                    String[] colonparts = pipeparts[0].split(":", 3);
                    if (colonparts.length < 3) {
                        System.err.println("Invalid instance format: " + arg);
                        System.exit(1);
                    }
                    // colonparts: [serviceName, instanceId, url]
                    // pipeparts[1] (optional): restartScript filename
                    String[] def = new String[pipeparts.length == 2 ? 4 : 3];
                    def[0] = colonparts[0];
                    def[1] = colonparts[1];
                    def[2] = colonparts[2];
                    if (pipeparts.length == 2) def[3] = pipeparts[1];
                    instanceDefs.add(def);
                }
            }
        }

        log.info("=== SecureNet Cluster Manager ===");
        log.info("  HTTP port:          " + port);
        log.info("  Check interval:     " + checkInterval + "ms");
        log.info("  Failure threshold:  " + failureThreshold + "ms");
        log.info("  Scripts dir:        " + scriptsDir);
        log.info("  Log dir:            " + logDir);
        log.info("  Node ID:            " + nodeId);
        log.info("  Cluster peers:      " + clusterPeers);
        log.info("  Instances:          " + instanceDefs.size());

        ClusterManager manager = new ClusterManager(checkInterval, failureThreshold, logDir);

        for (String[] def : instanceDefs) {
            String serviceName = def[0];
            String instanceId  = def[1];
            String url         = def[2];
            String restartScript = null;

            if (def.length == 4 && scriptsDir != null) {
                // Resolve script path relative to scripts dir
                restartScript = scriptsDir + "/" + def[3];
                // Make sure it's executable
                new java.io.File(restartScript).setExecutable(true);
            }

            manager.registerInstance(serviceName, instanceId, url, restartScript);
        }

        manager.start(initialDelay);

        String selfNodeId = nodeId != null ? nodeId : "cm-" + port;
        String selfUrl = "http://localhost:" + port;
        ClusterCoordinator coordinator = new ClusterCoordinator(
                selfNodeId,
                selfUrl,
                parseClusterPeers(clusterPeers, selfNodeId, selfUrl),
                manager);

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(2));

        httpServer.createContext("/cluster/status", ex -> {
            String json  = JsonUtil.toJson(manager.getStatus());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });

        httpServer.createContext("/cluster/state", ex -> {
            String json  = JsonUtil.toJson(manager.getStateSnapshot());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });

        httpServer.createContext("/cluster/leader", ex -> {
            String json = JsonUtil.toJson(coordinator.leaderStatus());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });

        httpServer.createContext("/health", ex -> {
            byte[] bytes = JsonUtil.toJson(Map.of(
                    "status", "UP",
                    "nodeId", selfNodeId,
                    "role", coordinator.currentRole()))
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });

        httpServer.start();
        coordinator.start();
        log.info("[ClusterManager] HTTP status endpoint on " + host + ":" + port);
        log.info("[ClusterManager] Check status: curl http://localhost:" + port
                + "/cluster/status");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            coordinator.stop();
            manager.stop();
            httpServer.stop(0);
            System.out.println("[ClusterManager] Shut down");
        }));

        Thread.currentThread().join();
    }

    private static LinkedHashMap<String, String> parseClusterPeers(String clusterPeers,
                                                                   String selfNodeId,
                                                                   String selfUrl) {
        LinkedHashMap<String, String> peers = new LinkedHashMap<>();
        if (clusterPeers != null && !clusterPeers.isBlank()) {
            for (String peer : clusterPeers.split(",")) {
                String trimmed = peer.trim();
                if (trimmed.isEmpty()) continue;
                String[] parts = trimmed.split("=", 2);
                if (parts.length == 2) {
                    peers.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        peers.putIfAbsent(selfNodeId, selfUrl);
        return peers;
    }

    private static final class ClusterCoordinator {
        private static final long ROLE_REFRESH_MS = 2000;

        private final String nodeId;
        private final String selfUrl;
        private final LinkedHashMap<String, String> peerUrls;
        private final ClusterManager manager;
        private final HttpClient httpClient = HttpClient.newBuilder().build();
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cluster-manager-coordinator");
            t.setDaemon(true);
            return t;
        });

        private volatile String activeLeaderId;
        private volatile String activeLeaderUrl;
        private volatile String role = "FOLLOWER";

        private ClusterCoordinator(String nodeId,
                                   String selfUrl,
                                   LinkedHashMap<String, String> peerUrls,
                                   ClusterManager manager) {
            this.nodeId = nodeId;
            this.selfUrl = selfUrl;
            this.peerUrls = peerUrls;
            this.manager = manager;
        }

        private void start() {
            scheduler.scheduleAtFixedRate(this::refreshRole, 0, ROLE_REFRESH_MS, TimeUnit.MILLISECONDS);
        }

        private void stop() {
            scheduler.shutdownNow();
        }

        private Map<String, Object> leaderStatus() {
            return Map.of(
                    "nodeId", activeLeaderId == null ? "" : activeLeaderId,
                    "url", activeLeaderUrl == null ? "" : activeLeaderUrl,
                    "active", "LEADER".equals(role),
                    "role", role);
        }

        private String currentRole() {
            return role;
        }

        @SuppressWarnings("unchecked")
        private void refreshRole() {
            try {
                LeaderView activeView = discoverActiveLeader();
                if (activeView != null) {
                    applyRole(activeView.nodeId.equals(nodeId), activeView.nodeId, activeView.url);
                    if (!activeView.nodeId.equals(nodeId)) {
                        syncFromLeader(activeView.url);
                    }
                    return;
                }

                MemberSelection elected = electLeader();
                if (elected == null) {
                    applyRole(false, null, null);
                    return;
                }

                applyRole(elected.nodeId.equals(nodeId), elected.nodeId, elected.url);
                if (!elected.nodeId.equals(nodeId)) {
                    syncFromLeader(elected.url);
                }
            } catch (Exception e) {
                log.warning("[ClusterManager] Coordinator refresh failed: " + e.getMessage());
            }
        }

        private void applyRole(boolean leader, String leaderId, String leaderUrl) {
            String newRole = leader ? "LEADER" : "FOLLOWER";
            if (!Objects.equals(role, newRole) || !Objects.equals(activeLeaderId, leaderId)) {
                log.info("[ClusterManager] Role update: " + nodeId + " -> " + newRole
                        + " leader=" + leaderId);
            }
            this.role = newRole;
            this.activeLeaderId = leaderId;
            this.activeLeaderUrl = leaderUrl;
            manager.setHealthChecksEnabled(leader);
        }

        private LeaderView discoverActiveLeader() {
            List<LeaderView> activeViews = new ArrayList<>();
            for (Map.Entry<String, String> entry : peerUrls.entrySet()) {
                String peerId = entry.getKey();
                String peerUrl = entry.getValue();
                if (peerId.equals(nodeId)) {
                    if ("LEADER".equals(role)) {
                        activeViews.add(new LeaderView(nodeId, selfUrl));
                    }
                    continue;
                }
                Map<String, Object> leaderView = getJson(peerUrl + "/cluster/leader");
                if (leaderView == null) continue;
                if (Boolean.TRUE.equals(leaderView.get("active"))) {
                    activeViews.add(new LeaderView(peerId, peerUrl));
                }
            }
            if (activeViews.isEmpty()) {
                return null;
            }
            activeViews.sort(Comparator.comparingInt(view -> priorityOf(view.nodeId)));
            return activeViews.get(0);
        }

        private MemberSelection electLeader() {
            for (Map.Entry<String, String> entry : peerUrls.entrySet()) {
                String peerId = entry.getKey();
                String peerUrl = entry.getValue();
                if (peerId.equals(nodeId)) {
                    return new MemberSelection(peerId, peerUrl);
                }
                if (isHealthy(peerUrl)) {
                    return new MemberSelection(peerId, peerUrl);
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private void syncFromLeader(String leaderUrl) {
            Map<String, Object> snapshot = getJson(leaderUrl + "/cluster/state");
            if (snapshot != null) {
                manager.replaceStateSnapshot(snapshot);
            }
        }

        private boolean isHealthy(String baseUrl) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(2))
                    .build();
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> getJson(String url) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(2))
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return null;
                }
                return JsonUtil.fromJson(response.body(), Map.class);
            } catch (Exception e) {
                return null;
            }
        }

        private int priorityOf(String memberId) {
            int i = 0;
            for (String id : peerUrls.keySet()) {
                if (id.equals(memberId)) {
                    return i;
                }
                i++;
            }
            return Integer.MAX_VALUE;
        }

        private record LeaderView(String nodeId, String url) {}
        private record MemberSelection(String nodeId, String url) {}
    }
}
