package com.securenet.eventprocessing.raft;

import com.securenet.common.JsonUtil;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * HTTP server exposing Raft protocol RPCs for inter-node communication.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /raft/request-vote}  — RequestVote RPC</li>
 *   <li>{@code POST /raft/append-entries} — AppendEntries RPC</li>
 *   <li>{@code GET  /raft/status}         — cluster status (debug)</li>
 * </ul>
 */
public class RaftRpcServer {

    private static final Logger log = Logger.getLogger(RaftRpcServer.class.getName());

    private final String host;
    private final int port;
    private final RaftNode raftNode;
    private HttpServer httpServer;

    public RaftRpcServer(String host, int port, RaftNode raftNode) {
        this.host     = Objects.requireNonNull(host, "host");
        this.port     = port;
        this.raftNode = Objects.requireNonNull(raftNode, "raftNode");
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(4));

        httpServer.createContext("/raft/request-vote",   this::handleRequestVote);
        httpServer.createContext("/raft/append-entries", this::handleAppendEntries);
        httpServer.createContext("/raft/status",         this::handleStatus);

        httpServer.start();
        log.info("[RaftRPC:" + raftNode.getNodeId() + "] started on " + host + ":" + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("[RaftRPC:" + raftNode.getNodeId() + "] stopped");
        }
    }

    // =====================================================================
    // POST /raft/request-vote
    // =====================================================================

    private void handleRequestVote(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body          = readBody(ex, Map.class);
            long term         = ((Number) body.get("term")).longValue();
            String candidateId = (String) body.get("candidateId");
            long lastLogIndex = ((Number) body.get("lastLogIndex")).longValue();
            long lastLogTerm  = ((Number) body.get("lastLogTerm")).longValue();

            log.fine("[RaftRPC:" + raftNode.getNodeId() + "] RequestVote from "
                    + candidateId + " term=" + term);

            Map<String, Object> result = raftNode.handleRequestVote(
                    term, candidateId, lastLogIndex, lastLogTerm);

            log.fine("[RaftRPC:" + raftNode.getNodeId() + "] RequestVote response: granted="
                    + result.get("voteGranted"));
            writeJson(ex, 200, result);
        } catch (Exception e) {
            log.severe("[RaftRPC:" + raftNode.getNodeId() + "] RequestVote error: "
                    + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // POST /raft/append-entries
    // =====================================================================

    private void handleAppendEntries(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map body          = readBody(ex, Map.class);
            long term         = ((Number) body.get("term")).longValue();
            String leaderId   = (String) body.get("leaderId");
            long prevLogIndex = ((Number) body.get("prevLogIndex")).longValue();
            long prevLogTerm  = ((Number) body.get("prevLogTerm")).longValue();
            long leaderCommit = ((Number) body.get("leaderCommit")).longValue();

            List<LogEntry> entries = List.of();
            Object rawEntries = body.get("entries");
            if (rawEntries != null) {
                String entriesJson = JsonUtil.toJson(rawEntries);
                entries = JsonUtil.gson().fromJson(entriesJson,
                        new TypeToken<List<LogEntry>>(){}.getType());
            }

            if (!entries.isEmpty()) {
                log.fine("[RaftRPC:" + raftNode.getNodeId() + "] AppendEntries from "
                        + leaderId + " term=" + term + " entries=" + entries.size());
            }

            Map<String, Object> result = raftNode.handleAppendEntries(
                    term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit);

            writeJson(ex, 200, result);
        } catch (Exception e) {
            log.severe("[RaftRPC:" + raftNode.getNodeId() + "] AppendEntries error: "
                    + e.getMessage());
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // =====================================================================
    // GET /raft/status
    // =====================================================================

    private void handleStatus(HttpExchange ex) throws IOException {
        log.fine("[RaftRPC:" + raftNode.getNodeId() + "] Status request");
        writeJson(ex, 200, raftNode.getStatus());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static <T> T readBody(HttpExchange ex, Class<T> clazz) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return JsonUtil.fromJson(new String(is.readAllBytes(), StandardCharsets.UTF_8), clazz);
        }
    }

    private static void writeJson(HttpExchange ex, int statusCode, Object body) throws IOException {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}