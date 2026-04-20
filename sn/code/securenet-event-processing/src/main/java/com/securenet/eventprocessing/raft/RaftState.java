package com.securenet.eventprocessing.raft;

/**
 * The three states a Raft node can be in.
 *
 * <p>State transitions follow this pattern:
 * <pre>
 *   FOLLOWER → CANDIDATE (election timeout fires, no heartbeat from leader)
 *   CANDIDATE → LEADER   (wins election with majority votes)
 *   CANDIDATE → FOLLOWER (discovers higher term or loses election)
 *   LEADER → FOLLOWER    (discovers higher term from another node)
 * </pre>
 */
public enum RaftState {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
