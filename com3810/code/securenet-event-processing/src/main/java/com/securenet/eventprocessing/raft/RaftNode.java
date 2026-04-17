package com.securenet.eventprocessing.raft;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core Raft consensus node for the Event Processing Service cluster.
 *
 * <p>Implements the Raft protocol as described in the Stage 3 design
 * document, covering:
 *
 * <ul>
 *   <li><strong>Leader Election (DS Problem #1):</strong> Uses
 *       RequestVote RPCs with log-completeness check. A candidate
 *       can only win if its log is at least as up-to-date as a
 *       majority of nodes.</li>
 *   <li><strong>Log Replication (DS Problem #2):</strong> The leader
 *       appends entries to its log and sends AppendEntries RPCs to
 *       followers in parallel. An entry is committed only after a
 *       majority (quorum) acknowledges it.</li>
 *   <li><strong>Quorums (DS Problem #5):</strong> All operations
 *       require floor(N/2)+1 nodes. For a 3-node cluster, quorum is 2.</li>
 *   <li><strong>Cluster Manager / Failure Detection (DS Problem #4):</strong>
 *       Followers detect leader failure via election timeout and start
 *       a new election.</li>
 * </ul>
 *
 * <p>Raft RPCs are implemented over HTTP/JSON via {@link RaftRpcServer}.
 * Each node knows the addresses of all peers and communicates via
 * {@link ServiceClient}.
 *
 * <h3>Thread model</h3>
 * <ul>
 *   <li>Election timer thread — fires election timeout, starts elections</li>
 *   <li>Heartbeat thread (leader only) — sends periodic AppendEntries</li>
 *   <li>HTTP server threads — handle incoming RPCs and client requests</li>
 * </ul>
 */
public class RaftNode {

    private final String nodeId;
    private final List<String> peerUrls;
    private final ServiceClient httpClient;
    private final RaftLog log;

    // ----- Persistent Raft state (survives restarts in production) -----
    private long currentTerm = 0;
    private String votedFor = null;

    // ----- Volatile Raft state -----
    private volatile RaftState state = RaftState.FOLLOWER;
    private volatile String leaderId = null;
    private volatile long commitIndex = 0;
    private volatile long lastApplied = 0;

    // ----- Leader-only state (reinitialized on election) -----
    /** For each peer: index of next log entry to send. */
    private final Map<String, Long> nextIndex = new ConcurrentHashMap<>();
    /** For each peer: index of highest log entry known to be replicated. */
    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    // ----- Timing -----
    private static final int HEARTBEAT_INTERVAL_MS = 150;
    private static final int ELECTION_TIMEOUT_MIN_MS = 500;
    private static final int ELECTION_TIMEOUT_MAX_MS = 1000;
    private final Random random = new Random();
    private volatile long lastHeartbeatTime = System.currentTimeMillis();

    // ----- Threading -----
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ----- Callback for committed entries -----
    private final CommitCallback commitCallback;

    /**
     * Callback invoked when a log entry is committed (replicated to a
     * quorum). The EPS applies the entry to its state machine (persists
     * the event, assigns Lamport sequence number, etc.).
     */
    @FunctionalInterface
    public interface CommitCallback {
        void onCommit(LogEntry entry);
    }

    /**
     * @param nodeId         unique identifier for this node (e.g. "eps-1")
     * @param peerUrls       HTTP base URLs of all other nodes in the cluster
     * @param commitCallback called when entries are committed
     */
    public RaftNode(String nodeId, List<String> peerUrls, CommitCallback commitCallback) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.peerUrls = List.copyOf(peerUrls);
        this.commitCallback = Objects.requireNonNull(commitCallback, "commitCallback");
        this.httpClient = new ServiceClient();
        this.log = new RaftLog();
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    /** Starts the election timer and heartbeat threads. */
    public void start() {
        running.set(true);
        lastHeartbeatTime = System.currentTimeMillis();

        // Election timer — checks periodically if we should start an election
        scheduler.scheduleAtFixedRate(this::electionTimerTick,
                randomElectionTimeout(), 50, TimeUnit.MILLISECONDS);

        System.out.println("[Raft:" + nodeId + "] Started as FOLLOWER, term=" + currentTerm +
                ", peers=" + peerUrls.size());
    }

    /** Stops all background threads. */
    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
        System.out.println("[Raft:" + nodeId + "] Stopped");
    }

    // =====================================================================
    // Election timer
    // =====================================================================

    private void electionTimerTick() {
        if (!running.get()) return;
        if (state == RaftState.LEADER) return;

        long elapsed = System.currentTimeMillis() - lastHeartbeatTime;
        if (elapsed >= randomElectionTimeout()) {
            startElection();
        }
    }

    // =====================================================================
    // Leader Election (DS Problem #1)
    // =====================================================================

    /**
     * Starts a new election. This node transitions to CANDIDATE,
     * increments its term, votes for itself, and sends RequestVote
     * RPCs to all peers.
     *
     * <p>Per the Raft paper, a candidate wins if it receives votes
     * from a majority of the cluster (including itself).
     *
     * <p>The log-completeness check ensures that a candidate can only
     * win if its log is at least as up-to-date as a majority of nodes.
     * This guarantees the new leader has all committed entries.
     */
    private synchronized void startElection() {
        if (state == RaftState.LEADER) return;

        currentTerm++;
        state = RaftState.CANDIDATE;
        votedFor = nodeId;
        leaderId = null;
        lastHeartbeatTime = System.currentTimeMillis();

        long electionTerm = currentTerm;
        System.out.println("[Raft:" + nodeId + "] Starting election for term " + electionTerm);

        // Count votes — we vote for ourselves
        int clusterSize = peerUrls.size() + 1;
        int quorum = (clusterSize / 2) + 1;
        int[] votes = {1}; // self-vote

        // Send RequestVote to all peers in parallel
        CountDownLatch latch = new CountDownLatch(peerUrls.size());

        for (String peerUrl : peerUrls) {
            scheduler.execute(() -> {
                try {
                    Map<String, Object> request = Map.of(
                            "term", electionTerm,
                            "candidateId", nodeId,
                            "lastLogIndex", log.getLastIndex(),
                            "lastLogTerm", log.getLastTerm()
                    );

                    ServiceResponse resp = httpClient.post(
                            peerUrl + "/raft/request-vote", request);

                    if (resp.isSuccess()) {
                        Map result = JsonUtil.fromJson(resp.body(), Map.class);
                        long respTerm = ((Number) result.get("term")).longValue();
                        boolean granted = Boolean.TRUE.equals(result.get("voteGranted"));

                        synchronized (RaftNode.this) {
                            if (respTerm > currentTerm) {
                                stepDown(respTerm);
                                return;
                            }
                            if (granted && state == RaftState.CANDIDATE &&
                                    currentTerm == electionTerm) {
                                votes[0]++;
                                if (votes[0] >= quorum) {
                                    becomeLeader();
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    // Peer unreachable — expected during failures
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    /**
     * Transitions this node to LEADER. Initializes nextIndex and
     * matchIndex for all peers and starts sending heartbeats.
     */
    private synchronized void becomeLeader() {
        if (state == RaftState.LEADER) return;

        state = RaftState.LEADER;
        leaderId = nodeId;

        // Initialize leader state (Raft paper §5.3)
        long lastIdx = log.getLastIndex();
        for (String peer : peerUrls) {
            nextIndex.put(peer, lastIdx + 1);
            matchIndex.put(peer, 0L);
        }

        System.out.println("[Raft:" + nodeId + "] Became LEADER for term " + currentTerm);

        // Start heartbeat loop
        scheduler.scheduleAtFixedRate(this::sendHeartbeats,
                0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Steps down to FOLLOWER upon discovering a higher term.
     * Resets votedFor so this node can vote in the new term.
     */
    private synchronized void stepDown(long newTerm) {
        currentTerm = newTerm;
        state = RaftState.FOLLOWER;
        votedFor = null;
        System.out.println("[Raft:" + nodeId + "] Stepped down to FOLLOWER, term=" + newTerm);
    }

    // =====================================================================
    // RequestVote RPC handler (called by candidates)
    // =====================================================================

    /**
     * Handles an incoming RequestVote RPC from a candidate.
     *
     * <p>Grants vote if:
     * <ol>
     *   <li>The candidate's term is ≥ our term</li>
     *   <li>We haven't voted for someone else in this term</li>
     *   <li>The candidate's log is at least as up-to-date as ours
     *       (log-completeness check per Raft §5.4.1)</li>
     * </ol>
     *
     * @return map with "term" and "voteGranted"
     */
    public synchronized Map<String, Object> handleRequestVote(
            long candidateTerm, String candidateId,
            long lastLogIndex, long lastLogTerm) {

        // If candidate has higher term, step down
        if (candidateTerm > currentTerm) {
            stepDown(candidateTerm);
        }

        boolean granted = false;

        if (candidateTerm >= currentTerm) {
            boolean notVotedOrSameCandidate =
                    (votedFor == null || votedFor.equals(candidateId));

            // Log-completeness check: candidate's log must be at least
            // as up-to-date as ours
            boolean logOk = (lastLogTerm > log.getLastTerm()) ||
                    (lastLogTerm == log.getLastTerm() &&
                            lastLogIndex >= log.getLastIndex());

            if (notVotedOrSameCandidate && logOk) {
                votedFor = candidateId;
                granted = true;
                lastHeartbeatTime = System.currentTimeMillis();
                System.out.println("[Raft:" + nodeId + "] Voted for " + candidateId +
                        " in term " + candidateTerm);
            }
        }

        return Map.of("term", currentTerm, "voteGranted", granted);
    }

    // =====================================================================
    // AppendEntries RPC handler (called by leader)
    // =====================================================================

    /**
     * Handles an incoming AppendEntries RPC from the leader.
     *
     * <p>This serves as both a heartbeat (when entries is empty) and
     * a log replication message (when entries is non-empty).
     *
     * <p>The consistency check ensures the follower's log matches the
     * leader's up to prevLogIndex/prevLogTerm. If there's a conflict,
     * the follower truncates its log and appends the leader's entries.
     *
     * @return map with "term" and "success"
     */
    public synchronized Map<String, Object> handleAppendEntries(
            long leaderTerm, String leaderIdParam,
            long prevLogIndex, long prevLogTerm,
            List<LogEntry> entries, long leaderCommit) {

        // Stale leader
        if (leaderTerm < currentTerm) {
            return Map.of("term", currentTerm, "success", false);
        }

        // Valid leader — reset election timer, step down if needed
        if (leaderTerm > currentTerm) {
            stepDown(leaderTerm);
        }

        state = RaftState.FOLLOWER;
        leaderId = leaderIdParam;
        lastHeartbeatTime = System.currentTimeMillis();

        // Consistency check — does our log match at prevLogIndex?
        if (prevLogIndex > 0) {
            long termAtPrev = log.getTermAt(prevLogIndex);
            if (termAtPrev == 0 || termAtPrev != prevLogTerm) {
                // Log doesn't match — follower needs to go back further
                return Map.of("term", currentTerm, "success", false);
            }
        }

        // Append new entries (with conflict resolution)
        if (entries != null && !entries.isEmpty()) {
            for (LogEntry entry : entries) {
                long existingTerm = log.getTermAt(entry.index());
                if (existingTerm != 0 && existingTerm != entry.term()) {
                    // Conflict — truncate from this point
                    log.truncateFrom(entry.index());
                }
                if (log.getLastIndex() < entry.index()) {
                    log.append(entry);
                }
            }
        }

        // Update commit index
        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log.getLastIndex());
            applyCommittedEntries();
        }

        return Map.of("term", currentTerm, "success", true);
    }

    // =====================================================================
    // Log Replication — leader sends AppendEntries (DS Problem #2)
    // =====================================================================

    /** Leader heartbeat: sends AppendEntries to all peers. */
    private void sendHeartbeats() {
        if (!running.get() || state != RaftState.LEADER) return;

        for (String peerUrl : peerUrls) {
            scheduler.execute(() -> sendAppendEntries(peerUrl));
        }
    }

    /**
     * Sends an AppendEntries RPC to a single peer. Includes any new
     * entries the peer hasn't seen yet (based on nextIndex).
     */
    private void sendAppendEntries(String peerUrl) {
        if (state != RaftState.LEADER) return;

        try {
            long peerNextIdx = nextIndex.getOrDefault(peerUrl, log.getLastIndex() + 1);
            long prevIdx = peerNextIdx - 1;
            long prevTerm = log.getTermAt(prevIdx);
            List<LogEntry> entries = log.getEntriesFrom(peerNextIdx);

            Map<String, Object> request = new HashMap<>();
            request.put("term", currentTerm);
            request.put("leaderId", nodeId);
            request.put("prevLogIndex", prevIdx);
            request.put("prevLogTerm", prevTerm);
            request.put("entries", entries);
            request.put("leaderCommit", commitIndex);

            ServiceResponse resp = httpClient.post(
                    peerUrl + "/raft/append-entries", request);

            if (resp.isSuccess()) {
                Map result = JsonUtil.fromJson(resp.body(), Map.class);
                long respTerm = ((Number) result.get("term")).longValue();
                boolean success = Boolean.TRUE.equals(result.get("success"));

                synchronized (this) {
                    if (respTerm > currentTerm) {
                        stepDown(respTerm);
                        return;
                    }

                    if (success) {
                        if (!entries.isEmpty()) {
                            long newMatchIdx = entries.get(entries.size() - 1).index();
                            nextIndex.put(peerUrl, newMatchIdx + 1);
                            matchIndex.put(peerUrl, newMatchIdx);
                            advanceCommitIndex();
                        }
                    } else {
                        // Decrement nextIndex and retry on next heartbeat
                        long newNext = Math.max(1, peerNextIdx - 1);
                        nextIndex.put(peerUrl, newNext);
                    }
                }
            }
        } catch (IOException e) {
            // Peer unreachable — will retry on next heartbeat
        }
    }

    /**
     * Advances the commit index if a majority of nodes have replicated
     * up to a new index. Per Raft §5.4.2, the leader only commits
     * entries from its own term (entries from prior terms become
     * committed as a side effect).
     */
    private synchronized void advanceCommitIndex() {
        for (long idx = commitIndex + 1; idx <= log.getLastIndex(); idx++) {
            LogEntry entry = log.getEntry(idx);
            if (entry == null || entry.term() != currentTerm) continue;

            // Count replicas (including self)
            int replicatedCount = 1;
            for (String peer : peerUrls) {
                if (matchIndex.getOrDefault(peer, 0L) >= idx) {
                    replicatedCount++;
                }
            }

            int quorum = (peerUrls.size() + 1) / 2 + 1;
            if (replicatedCount >= quorum) {
                commitIndex = idx;
            }
        }

        applyCommittedEntries();
    }

    // =====================================================================
    // State machine application
    // =====================================================================

    /**
     * Applies all committed but not-yet-applied entries to the state
     * machine via the {@link CommitCallback}.
     */
    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = log.getEntry(lastApplied);
            if (entry != null) {
                try {
                    commitCallback.onCommit(entry);
                } catch (Exception e) {
                    System.err.println("[Raft:" + nodeId + "] Error applying entry " +
                            lastApplied + ": " + e.getMessage());
                }
            }
        }
    }

    // =====================================================================
    // Client request — append to leader's log
    // =====================================================================

    /**
     * Called by the EPS when a new event arrives. If this node is the
     * leader, it appends the entry to the log and begins replication.
     * If not, it returns {@code null} and the caller should redirect
     * to the leader.
     *
     * <p>Returns a future that completes when the entry is committed
     * (replicated to a quorum).
     *
     * @return a CompletableFuture that completes with the committed
     *         LogEntry, or {@code null} if this node is not the leader
     */
    public synchronized CompletableFuture<LogEntry> appendEntry(
            String deviceId, String eventType, String occurredAt,
            String nonce, Map<String, String> metadata) {

        if (state != RaftState.LEADER) {
            return null;
        }

        long newIndex = log.getLastIndex() + 1;
        LogEntry entry = new LogEntry(
                newIndex, currentTerm,
                deviceId, eventType, occurredAt, nonce, metadata
        );

        log.append(entry);

        // The entry will be replicated on the next heartbeat cycle
        // (within HEARTBEAT_INTERVAL_MS). For immediate replication,
        // we trigger a heartbeat now.
        scheduler.execute(this::sendHeartbeats);

        // Return a future that completes when this entry is committed
        CompletableFuture<LogEntry> future = new CompletableFuture<>();

        // Monitor commit progress on a background thread
        scheduler.execute(() -> {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline && running.get()) {
                if (lastApplied >= newIndex) {
                    future.complete(entry);
                    return;
                }
                try { Thread.sleep(10); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!future.isDone()) {
                future.completeExceptionally(
                        new TimeoutException("Entry " + newIndex + " not committed within timeout"));
            }
        });

        return future;
    }

    // =====================================================================
    // Accessors
    // =====================================================================

    public String getNodeId() { return nodeId; }
    public RaftState getState() { return state; }
    public long getCurrentTerm() { return currentTerm; }
    public String getLeaderId() { return leaderId; }
    public long getCommitIndex() { return commitIndex; }
    public long getLastApplied() { return lastApplied; }
    public RaftLog getLog() { return log; }

    public boolean isLeader() { return state == RaftState.LEADER; }

    /** Returns a status map for health/debug endpoints. */
    public Map<String, Object> getStatus() {
        return Map.of(
                "nodeId", nodeId,
                "state", state.name(),
                "term", currentTerm,
                "leaderId", leaderId != null ? leaderId : "none",
                "commitIndex", commitIndex,
                "lastApplied", lastApplied,
                "logSize", log.size()
        );
    }

    private int randomElectionTimeout() {
        return ELECTION_TIMEOUT_MIN_MS +
                random.nextInt(ELECTION_TIMEOUT_MAX_MS - ELECTION_TIMEOUT_MIN_MS);
    }
}
