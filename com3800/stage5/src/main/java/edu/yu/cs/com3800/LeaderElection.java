package edu.yu.cs.com3800;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**We are implementing a simplified version of the election algorithm. For the complete version which covers all possible scenarios, see https://github.com/apache/zookeeper/blob/90f8d835e065ea12dddd8ed9ca20872a4412c78a/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java#L913
 */
public class LeaderElection {
    /**
     * time to wait once we believe we've reached the end of leader election.
     */
    private final static int finalizeWait = 3200;
    private Logger logger;
    private PeerServer parentServer;
    private final LinkedBlockingQueue<Message> incomingMessages;
    private final ConcurrentHashMap<Long, PeerServer.ServerState> serversToState = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Long> followers = new CopyOnWriteArrayList<>();

    /**
     * Upper bound on the amount of time between two consecutive notification checks.
     * This impacts the amount of time to get the system up again after long partitions. Currently 30 seconds.
     */
    private final static int maxNotificationInterval = 30000;
    private long proposedEpoch;
    private long proposedLeader;

    public LeaderElection(PeerServer server, LinkedBlockingQueue<Message> incomingMessages, Logger logger) {
        this.logger = logger;
        this.parentServer = server;
        this.incomingMessages = incomingMessages;
        this.proposedLeader = this.parentServer.getServerId();
        this.proposedEpoch =this.parentServer.getPeerEpoch();
    }


    public static byte[] buildMsgContent(ElectionNotification notification) {

        //long 8 bytes proposedLeaderID
        //long 8 bytes senderID
        //long 8 bytes peerEpoch
        //char 2 byte ServerState
        //=26 bytes

        int bufferSize = 26;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        buffer.clear();
        buffer.putLong(notification.getProposedLeaderID());
        buffer.putLong(notification.getSenderID());
        buffer.putLong(notification.getPeerEpoch());
        buffer.putChar(notification.getState().getChar());
        buffer.flip();
        return buffer.array();

    }


    public static ElectionNotification getNotificationFromMessage(Message received) {
        byte[] contents = received.getMessageContents();
        ByteBuffer buffer = ByteBuffer.wrap(contents);
        buffer.clear();
        long proposedLeaderID = buffer.getLong();
        long senderID = buffer.getLong();
        long peerEpoch = buffer.getLong();
        PeerServer.ServerState serverState = PeerServer.ServerState.getServerState(buffer.getChar());
        return new ElectionNotification(proposedLeaderID, serverState, senderID, peerEpoch);
    }

    /**
     * Note that the logic in the comments below does NOT cover every last "technical" detail you will need to address to implement the election algorithm.
     * How you store all the relevant state, etc., are details you will need to work out.
     * @return the elected leader
     */
    public synchronized Vote lookForLeader() {
        //System.out.println("Server" + this.parentServer.getServerId() + " now in leader election with epoch " + this.proposedEpoch);
        try {
            sendInitialNotifications();
            Map<Long, ElectionNotification> votesForRound = new HashMap<>();
            if (!this.parentServer.getPeerState().equals(PeerServer.ServerState.OBSERVER)) {
                votesForRound.put(this.parentServer.getServerId(),
                        new ElectionNotification(this.proposedLeader, this.parentServer.getPeerState(), this.parentServer.getServerId(), this.proposedEpoch));
            }

            Vote oneNode = checkIfOneNodeCluster(votesForRound);
            if (oneNode != null) {
                return oneNode;
            }

            long retryTimeout = 200;
            while(true) {
                Message message = this.incomingMessages.poll(retryTimeout, TimeUnit.MILLISECONDS);

                if (message == null) {
                    this.logger.log(Level.FINE, "No message received, waiting: " + retryTimeout + "ms before retrying");
                    Thread.sleep(retryTimeout);
                    retryTimeout = Math.min(retryTimeout * 2, maxNotificationInterval);
                    sendInitialNotifications();
                    continue;

                }
                retryTimeout = 200;

                ElectionNotification electionNotification = getNotificationFromMessage(message);


                if (electionNotification.getPeerEpoch() < this.proposedEpoch) {
                    continue;
                }

                if (this.parentServer.isPeerDead(electionNotification.getProposedLeaderID())) {
                    continue;
                }

                boolean isSenderObserver = electionNotification.getState() == PeerServer.ServerState.OBSERVER;
                if (isSenderObserver) {
                    this.serversToState.put(electionNotification.getSenderID(), PeerServer.ServerState.OBSERVER);
                    continue;
                }

                if (electionNotification.getState() == PeerServer.ServerState.FOLLOWING || electionNotification.getState() == PeerServer.ServerState.LEADING) {
                    this.logger.log(Level.FINE, "Found leader");
                    return acceptElectionWinner(electionNotification, votesForRound);
                }



                if (!isSenderObserver) {
                    votesForRound.put(electionNotification.getSenderID(), electionNotification);
                }
                boolean supersedes = supersedesCurrentVote(electionNotification.getProposedLeaderID(), electionNotification.getPeerEpoch());
                // changed vote
                if (supersedes && !isSenderObserver) {
                    this.logger.log(Level.FINE, "Received higher vote from server " + electionNotification.getSenderID() + ". Changing vote from " + this.proposedLeader + " to " + electionNotification.getProposedLeaderID());
                    this.proposedLeader = electionNotification.getProposedLeaderID();
                    this.proposedEpoch = electionNotification.getPeerEpoch();
                    this.parentServer.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(
                            new ElectionNotification(this.proposedLeader, this.parentServer.getPeerState(), this.parentServer.getServerId(), this.proposedEpoch)));
                }

                Vote proposedVote = new Vote(this.proposedLeader, this.proposedEpoch);
                boolean hasEnoughVotes = haveEnoughVotes(votesForRound, proposedVote);
                if (hasEnoughVotes) {
                    // do a last check to see if there are any new votes for a higher ranked possible leader. If there are, continue in my election "while" loop.
                    // for the duration of finalize wait keep trying to poll messages off the queue to see if there is a higher vote
                    long waitUntil = System.currentTimeMillis() + finalizeWait;
                    boolean higherVoteFound = false;
                    while (System.currentTimeMillis() < waitUntil) {
                        Message possibleHigherVote = this.incomingMessages.poll(200, TimeUnit.MILLISECONDS);
                        if (possibleHigherVote == null) {
                            continue;
                        }

                        ElectionNotification notification = getNotificationFromMessage(possibleHigherVote);
                        if (notification.getState().equals(PeerServer.ServerState.OBSERVER)) {
                            this.serversToState.put(notification.getSenderID(), PeerServer.ServerState.OBSERVER);
                            continue;
                        }

                        if (supersedesCurrentVote(notification.getProposedLeaderID(), notification.getPeerEpoch())) {
                            higherVoteFound = true;
                            // set proposed leader to new higher message
                            this.logger.log(Level.FINE, "Found higher vote during leader coronation from server " + notification.getSenderID() +", Cancelling coronation. Changing vote from " + this.proposedLeader + " to " + notification.getProposedLeaderID());
                            this.proposedLeader = notification.getProposedLeaderID();
                            this.proposedEpoch = notification.getPeerEpoch();
                            this.parentServer.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(
                                    new ElectionNotification(this.proposedLeader, this.parentServer.getPeerState(), this.parentServer.getServerId(), this.proposedEpoch)));
                            //Thread.sleep(3000);
                            break;
                        } else if (notification.getState().equals(PeerServer.ServerState.LOOKING) && notification.getPeerEpoch() == this.proposedEpoch
                                && !this.parentServer.isPeerDead(notification.getProposedLeaderID()) && notification.getProposedLeaderID() == this.proposedLeader) {
                            votesForRound.put(notification.getSenderID(), notification);

                        }
                    }
                    if (!higherVoteFound) {
                        Vote winner = acceptElectionWinner(electionNotification, votesForRound);
                        votesForRound.clear();
                        return winner;
                    }
                }

            }
        }
        catch (InterruptedException e) {
            logger.log(Level.WARNING, "Election interrupted, continuing election");
            //Thread.currentThread().interrupt();

        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected exception in leader election", e);
        }

        return null;
    }

    private Vote checkIfOneNodeCluster(Map<Long, ElectionNotification> votesForRound) throws InterruptedException {
        Vote initialProposal = new Vote(this.proposedLeader, this.proposedEpoch);
        if (haveEnoughVotes(votesForRound, initialProposal)) {
            long waitUntil = System.currentTimeMillis() + finalizeWait;
            boolean higherVoteFound = false;
            while (System.currentTimeMillis() < waitUntil) {
                Message possibleHigher = this.incomingMessages.poll(200, TimeUnit.MILLISECONDS);
                if (possibleHigher == null) {
                    continue;
                }
                ElectionNotification n = getNotificationFromMessage(possibleHigher);
                if (supersedesCurrentVote(n.getProposedLeaderID(), n.getPeerEpoch())) {
                    higherVoteFound = true;
                    // found higher vote, not a one node cluster
                    this.proposedLeader = n.getProposedLeaderID();
                    this.proposedEpoch = n.getPeerEpoch();
                    this.parentServer.sendBroadcast(
                            Message.MessageType.ELECTION,
                            buildMsgContent(new ElectionNotification(this.proposedLeader,
                                    this.parentServer.getPeerState(), this.parentServer.getServerId(), this.proposedEpoch))
                    );
                    break;
                }
            }
            if (!higherVoteFound) {
                return acceptElectionWinner(new ElectionNotification(
                        this.proposedLeader, this.parentServer.getPeerState(),
                        this.parentServer.getServerId(), this.proposedEpoch), votesForRound);
            }
        }
        return null;
    }

    private synchronized void sendInitialNotifications() {
        ElectionNotification electionNotification = new ElectionNotification(this.proposedLeader, this.parentServer.getPeerState(), this.parentServer.getServerId(), this.proposedEpoch);
        this.logger.log(Level.FINE, "Sending Initial vote. Voting for: " + electionNotification.getProposedLeaderID());
        this.parentServer.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(electionNotification));
    }

    private Vote acceptElectionWinner(ElectionNotification n, Map<Long, ElectionNotification> votesForRound) {
        //set my state to either LEADING or FOLLOWING
        //clear out the incoming queue before returning


        Vote leaderVote = new Vote(n.getProposedLeaderID(), n.getPeerEpoch());
        if (n.getProposedLeaderID() == this.parentServer.getServerId()) {
            this.parentServer.setPeerState(PeerServer.ServerState.LEADING);
            this.logger.log(Level.FINE, "Set server state to LEADING");

            //need to get all peers that voted

            this.serversToState.put(this.parentServer.getServerId(), PeerServer.ServerState.LEADING);
            for (Long id : votesForRound.keySet()) {
                if (!Objects.equals(id, this.parentServer.getServerId())) {
                    if (!this.followers.contains(id)) {
                        //System.out.println("[LE] Adding follower from election: " + id);
                        this.followers.add(id);
                    } else {
                        //System.out.println("[LE] Duplicate follower ignored from election: " + id);
                    }
                    this.serversToState.put(id, PeerServer.ServerState.FOLLOWING);
                }
            }

        } else if (!this.parentServer.getPeerState().equals(PeerServer.ServerState.OBSERVER)) {
            this.parentServer.setPeerState(PeerServer.ServerState.FOLLOWING);
            logger.log(Level.FINE, "Set server state to FOLLOWING");
        }

        try {
            this.parentServer.setCurrentLeader(leaderVote);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error setting current leader", e);
        }

        ElectionNotification broadcastWinner =
                new ElectionNotification(leaderVote.getProposedLeaderID(), this.parentServer.getPeerState(), this.parentServer.getServerId(), leaderVote.getPeerEpoch());
        this.parentServer.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(broadcastWinner));

        this.incomingMessages.clear();
        this.logger.log(Level.FINE, "Elected " + leaderVote.getProposedLeaderID() + " as leader");
        return leaderVote;

    }

    public ConcurrentHashMap<Long, PeerServer.ServerState> getServerStates() {
        return new ConcurrentHashMap<>(this.serversToState);
    }

    public CopyOnWriteArrayList<Long> getFollowers () {
        return this.followers;
    }

    /**
     * We return true if one of the following two cases hold:
     * 1- New epoch is higher
     * 2- New epoch is the same as current epoch, but server id is higher.
     */
    protected boolean supersedesCurrentVote(long newId, long newEpoch) {
        return (newEpoch > this.proposedEpoch) || ((newEpoch == this.proposedEpoch) && (newId > this.proposedLeader));
    }

    /**
     * Termination predicate. Given a set of votes, determines if we have sufficient support for the proposal to declare the end of the election round.
     * Who voted for who isn't relevant, we only care that each server has one current vote.
     */
    protected boolean haveEnoughVotes(Map<Long, ElectionNotification> votes, Vote proposal) {
        //TODO: clarify if all servers in quorum or just voting servers
        //votes should only have this rounds votes
        //Set<Long> votingServers = votes.keySet();
        //int totalVotingServers = votingServers.size();
        //how does each server know how many servers running there are?
        //int quorumSize = this.parentServer.getQuorumSize();
        /*if (quorumSize != totalVotingServers) {
            return false;
        }*/
        //List<ElectionNotification> allVotes = votes.values().stream().toList();
        long proposedServerID = proposal.getProposedLeaderID();
        int count = 0;
        for (ElectionNotification notification : votes.values()) {
            if (notification.getProposedLeaderID() == proposedServerID) count++;
        }
        return count >= this.parentServer.getQuorumSize();



        //is the number of votes for the proposal > the size of my peer server’s quorum?
    }
}