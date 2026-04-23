package com.securenet.eventprocessing.raft;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Core Raft consensus node for the Event Processing Service cluster.
 *
 * <p>Implements the Raft protocol covering:
 * <ul>
 *   <li><strong>Leader Election (DS Problem #1)</strong></li>
 *   <li><strong>Log Replication (DS Problem #2)</strong></li>
 *   <li><strong>Quorums (DS Problem #5)</strong></li>
 *   <li><strong>Failure Detection (DS Problem #4)</strong></li>
 * </ul>
 */
public class RaftNode {

    private static final Logger log = Logger.getLogger(RaftNode.class.getName());

    private final String nodeId;
    private final List<String> peerUrls;
    private final ServiceClient httpClient;
    private final RaftLog log2; // named log2 to avoid clash with Logger field 'log'

    private long currentTerm = 0;
    private String votedFor  = null;

    private volatile RaftState state    = RaftState.FOLLOWER;
    private volatile String leaderId    = null;
    private volatile long commitIndex   = 0;
    private volatile long lastApplied   = 0;

    private final Map<String, Long> nextIndex  = new ConcurrentHashMap<>();
    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    private static final int HEARTBEAT_INTERVAL_MS   = 150;
    private static final int ELECTION_TIMEOUT_MIN_MS = 2000;
    private static final int ELECTION_TIMEOUT_MAX_MS = 4000;
    private static final int COMMIT_TIMEOUT_MS       = 4500;
    private final Random random = new Random();
    private volatile long lastHeartbeatTime = System.currentTimeMillis();
    private volatile long electionDeadlineTime = System.currentTimeMillis();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService replicationExecutor;
    private volatile ScheduledFuture<?> heartbeatTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentMap<Long, CompletableFuture<LogEntry>> pendingCommits =
            new ConcurrentHashMap<>();

    private final CommitCallback commitCallback;

    @FunctionalInterface
    public interface CommitCallback {
        void onCommit(LogEntry entry);
    }

    public RaftNode(String nodeId, List<String> peerUrls, CommitCallback commitCallback) {
        this.nodeId         = Objects.requireNonNull(nodeId, "nodeId");
        this.peerUrls       = List.copyOf(peerUrls);
        this.commitCallback = Objects.requireNonNull(commitCallback, "commitCallback");
        this.httpClient     = new ServiceClient();
        this.log2           = new RaftLog();
        this.replicationExecutor = Executors.newFixedThreadPool(
                Math.max(4, this.peerUrls.size() * 4));
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    public void start() {
        running.set(true);
        lastHeartbeatTime = System.currentTimeMillis();
        resetElectionDeadline();

        scheduler.scheduleAtFixedRate(this::electionTimerTick,
                randomElectionTimeout(), 50, TimeUnit.MILLISECONDS);

        log.info("[Raft:" + nodeId + "] Started as FOLLOWER, term=" + currentTerm
                + ", peers=" + peerUrls.size());
    }

    public void stop() {
        running.set(false);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
        scheduler.shutdownNow();
        replicationExecutor.shutdownNow();
        System.out.println("[Raft:" + nodeId + "] Stopped");
    }

    // =====================================================================
    // Election timer
    // =====================================================================

    private void electionTimerTick() {
        if (!running.get()) return;
        if (state == RaftState.LEADER) return;

        if (System.currentTimeMillis() >= electionDeadlineTime) {
            startElection();
        }
    }

    // =====================================================================
    // Leader Election (DS Problem #1)
    // =====================================================================

    private synchronized void startElection() {
        if (state == RaftState.LEADER) return;

        currentTerm++;
        state    = RaftState.CANDIDATE;
        votedFor = nodeId;
        leaderId = null;
        lastHeartbeatTime = System.currentTimeMillis();
        resetElectionDeadline();

        long electionTerm = currentTerm;
        log.info("[Raft:" + nodeId + "] Starting election for term " + electionTerm);

        int clusterSize = peerUrls.size() + 1;
        int quorum      = (clusterSize / 2) + 1;
        int[] votes     = {1}; // self-vote

        CountDownLatch latch = new CountDownLatch(peerUrls.size());

        for (String peerUrl : peerUrls) {
            replicationExecutor.execute(() -> {
                try {
                    Map<String, Object> request = Map.of(
                            "term",         electionTerm,
                            "candidateId",  nodeId,
                            "lastLogIndex", log2.getLastIndex(),
                            "lastLogTerm",  log2.getLastTerm()
                    );

                    ServiceResponse resp = httpClient.post(
                            peerUrl + "/raft/request-vote", request);

                    if (resp.isSuccess()) {
                        Map result    = JsonUtil.fromJson(resp.body(), Map.class);
                        long respTerm = ((Number) result.get("term")).longValue();
                        boolean granted = Boolean.TRUE.equals(result.get("voteGranted"));

                        synchronized (RaftNode.this) {
                            if (respTerm > currentTerm) {
                                stepDown(respTerm);
                                return;
                            }
                            if (granted && state == RaftState.CANDIDATE
                                    && currentTerm == electionTerm) {
                                votes[0]++;
                                log.fine("[Raft:" + nodeId + "] Vote received from "
                                        + peerUrl + " total=" + votes[0]);
                                if (votes[0] >= quorum) {
                                    becomeLeader();
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    log.fine("[Raft:" + nodeId + "] RequestVote to " + peerUrl
                            + " failed (peer unreachable): " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    private synchronized void becomeLeader() {
        if (state == RaftState.LEADER) return;

        state    = RaftState.LEADER;
        leaderId = nodeId;

        long lastIdx = log2.getLastIndex();
        for (String peer : peerUrls) {
            nextIndex.put(peer, lastIdx + 1);
            matchIndex.put(peer, 0L);
        }

        log.info("[Raft:" + nodeId + "] Became LEADER for term " + currentTerm);

        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(this::sendHeartbeats,
                0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stepDown(long newTerm) {
        currentTerm = newTerm;
        state       = RaftState.FOLLOWER;
        votedFor    = null;
        leaderId    = null;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        resetElectionDeadline();
        log.info("[Raft:" + nodeId + "] Stepped down to FOLLOWER, term=" + newTerm);
    }

    // =====================================================================
    // RequestVote RPC handler
    // =====================================================================

    public synchronized Map<String, Object> handleRequestVote(
            long candidateTerm, String candidateId,
            long lastLogIndex, long lastLogTerm) {

        if (candidateTerm > currentTerm) {
            stepDown(candidateTerm);
        }

        boolean granted = false;

        if (candidateTerm >= currentTerm) {
            boolean notVotedOrSameCandidate =
                    (votedFor == null || votedFor.equals(candidateId));
            boolean logOk = (lastLogTerm > log2.getLastTerm()) ||
                    (lastLogTerm == log2.getLastTerm() &&
                            lastLogIndex >= log2.getLastIndex());

            if (notVotedOrSameCandidate && logOk) {
                votedFor = candidateId;
                granted  = true;
                lastHeartbeatTime = System.currentTimeMillis();
                resetElectionDeadline();
                log.info("[Raft:" + nodeId + "] Voted for " + candidateId
                        + " in term " + candidateTerm);
            }
        }

        if (!granted) {
            log.fine("[Raft:" + nodeId + "] Denied vote for " + candidateId
                    + " in term " + candidateTerm
                    + " (votedFor=" + votedFor + ")");
        }

        return Map.of("term", currentTerm, "voteGranted", granted);
    }

    // =====================================================================
    // AppendEntries RPC handler
    // =====================================================================

    public synchronized Map<String, Object> handleAppendEntries(
            long leaderTerm, String leaderIdParam,
            long prevLogIndex, long prevLogTerm,
            List<LogEntry> entries, long leaderCommit) {

        if (leaderTerm < currentTerm) {
            log.fine("[Raft:" + nodeId + "] Rejected stale AppendEntries from "
                    + leaderIdParam + " (their term=" + leaderTerm
                    + " < ours=" + currentTerm + ")");
            return Map.of("term", currentTerm, "success", false);
        }

        if (leaderTerm > currentTerm) {
            stepDown(leaderTerm);
        }

        state    = RaftState.FOLLOWER;
        leaderId = leaderIdParam;
        lastHeartbeatTime = System.currentTimeMillis();
        resetElectionDeadline();

        if (prevLogIndex > 0) {
            long termAtPrev = log2.getTermAt(prevLogIndex);
            if (termAtPrev == 0 || termAtPrev != prevLogTerm) {
                log.fine("[Raft:" + nodeId + "] Consistency check failed: prevLogIndex="
                        + prevLogIndex + " expected term=" + prevLogTerm
                        + " got=" + termAtPrev);
                return Map.of("term", currentTerm, "success", false);
            }
        }

        if (entries != null && !entries.isEmpty()) {
            log.fine("[Raft:" + nodeId + "] Appending " + entries.size()
                    + " entries from leader=" + leaderIdParam);
            for (LogEntry entry : entries) {
                long existingTerm = log2.getTermAt(entry.index());
                if (existingTerm != 0 && existingTerm != entry.term()) {
                    log.warning("[Raft:" + nodeId + "] Log conflict at index=" + entry.index()
                            + " — truncating");
                    log2.truncateFrom(entry.index());
                }
                if (log2.getLastIndex() < entry.index()) {
                    log2.append(entry);
                }
            }
        }

        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log2.getLastIndex());
            log.fine("[Raft:" + nodeId + "] commitIndex advanced to " + commitIndex);
            applyCommittedEntries();
        }

        return Map.of("term", currentTerm, "success", true);
    }

    // =====================================================================
    // Log Replication
    // =====================================================================

    private void sendHeartbeats() {
        if (!running.get() || state != RaftState.LEADER) return;
        for (String peerUrl : peerUrls) {
            replicationExecutor.execute(() -> sendAppendEntries(peerUrl));
        }
    }

    private void sendAppendEntries(String peerUrl) {
        if (state != RaftState.LEADER) return;

        try {
            long peerNextIdx = nextIndex.getOrDefault(peerUrl, log2.getLastIndex() + 1);
            long prevIdx     = peerNextIdx - 1;
            long prevTerm    = log2.getTermAt(prevIdx);
            List<LogEntry> entries = log2.getEntriesFrom(peerNextIdx);

            Map<String, Object> request = new HashMap<>();
            request.put("term",         currentTerm);
            request.put("leaderId",     nodeId);
            request.put("prevLogIndex", prevIdx);
            request.put("prevLogTerm",  prevTerm);
            request.put("entries",      entries);
            request.put("leaderCommit", commitIndex);

            ServiceResponse resp = httpClient.post(
                    peerUrl + "/raft/append-entries", request);

            if (resp.isSuccess()) {
                Map result    = JsonUtil.fromJson(resp.body(), Map.class);
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
                            log.fine("[Raft:" + nodeId + "] Replicated up to index="
                                    + newMatchIdx + " on peer=" + peerUrl);
                            advanceCommitIndex();
                        }
                    } else {
                        long newNext = Math.max(1, peerNextIdx - 1);
                        nextIndex.put(peerUrl, newNext);
                        log.fine("[Raft:" + nodeId + "] Replication failed for peer="
                                + peerUrl + " — backing off to nextIndex=" + newNext);
                    }
                }
            }
        } catch (IOException e) {
            // Peer unreachable — will retry on next heartbeat
            log.fine("[Raft:" + nodeId + "] AppendEntries to " + peerUrl
                    + " failed (peer unreachable)");
        }
    }

    private synchronized void advanceCommitIndex() {
        for (long idx = commitIndex + 1; idx <= log2.getLastIndex(); idx++) {
            LogEntry entry = log2.getEntry(idx);
            if (entry == null || entry.term() != currentTerm) continue;

            int replicatedCount = 1;
            for (String peer : peerUrls) {
                if (matchIndex.getOrDefault(peer, 0L) >= idx) {
                    replicatedCount++;
                }
            }

            int quorum = (peerUrls.size() + 1) / 2 + 1;
            if (replicatedCount >= quorum) {
                commitIndex = idx;
                log.fine("[Raft:" + nodeId + "] Quorum reached for index=" + idx
                        + " (replicated=" + replicatedCount + "/" + (peerUrls.size() + 1) + ")");
            }
        }

        applyCommittedEntries();
    }

    // =====================================================================
    // State machine application
    // =====================================================================

    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = log2.getEntry(lastApplied);
            if (entry != null) {
                try {
                    log.fine("[Raft:" + nodeId + "] Applying entry index=" + lastApplied
                            + " deviceId=" + entry.deviceId());
                    commitCallback.onCommit(entry);
                } catch (Exception e) {
                    log.severe("[Raft:" + nodeId + "] Error applying entry "
                            + lastApplied + ": " + e.getMessage());
                    CompletableFuture<LogEntry> failed = pendingCommits.remove(lastApplied);
                    if (failed != null) {
                        failed.completeExceptionally(e);
                    }
                    continue;
                }
                CompletableFuture<LogEntry> future = pendingCommits.remove(lastApplied);
                if (future != null) {
                    log.info("[Raft:" + nodeId + "] Entry committed: index=" + lastApplied);
                    future.complete(entry);
                }
            }
        }
    }

    // =====================================================================
    // Client request — append to leader's log
    // =====================================================================

    public synchronized CompletableFuture<LogEntry> appendEntry(
            String deviceId, String eventType, String occurredAt,
            String nonce, Map<String, String> metadata) {

        if (state != RaftState.LEADER) {
            return null;
        }

        long newIndex = log2.getLastIndex() + 1;
        LogEntry entry = new LogEntry(
                newIndex, currentTerm,
                deviceId, eventType, occurredAt, nonce, metadata
        );

        log2.append(entry);
        log.info("[Raft:" + nodeId + "] Appended entry index=" + newIndex
                + " deviceId=" + deviceId + " type=" + eventType);

        CompletableFuture<LogEntry> future = new CompletableFuture<>();
        pendingCommits.put(newIndex, future);

        replicationExecutor.execute(this::sendHeartbeats);
        scheduler.schedule(() -> {
            CompletableFuture<LogEntry> timedOut = pendingCommits.remove(newIndex);
            if (timedOut != null) {
                log.severe("[Raft:" + nodeId + "] Commit timeout for index=" + newIndex);
                timedOut.completeExceptionally(
                        new TimeoutException("Entry " + newIndex + " not committed within timeout"));
            }
        }, COMMIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        return future;
    }

    // =====================================================================
    // Accessors
    // =====================================================================

    public String    getNodeId()      { return nodeId; }
    public RaftState getState()       { return state; }
    public long      getCurrentTerm() { return currentTerm; }
    public String    getLeaderId()    { return leaderId; }
    public long      getCommitIndex() { return commitIndex; }
    public long      getLastApplied() { return lastApplied; }
    public RaftLog   getLog()         { return log2; }
    public boolean   isLeader()       { return state == RaftState.LEADER; }

    public Map<String, Object> getStatus() {
        return Map.of(
                "nodeId",      nodeId,
                "state",       state.name(),
                "term",        currentTerm,
                "leaderId",    leaderId != null ? leaderId : "none",
                "commitIndex", commitIndex,
                "lastApplied", lastApplied,
                "logSize",     log2.size()
        );
    }

    private int randomElectionTimeout() {
        return ELECTION_TIMEOUT_MIN_MS +
                random.nextInt(ELECTION_TIMEOUT_MAX_MS - ELECTION_TIMEOUT_MIN_MS + 1);
    }

    private void resetElectionDeadline() {
        electionDeadlineTime = System.currentTimeMillis() + randomElectionTimeout();
    }
}
