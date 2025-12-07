package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayPeerServerImpl extends PeerServerImpl implements Runnable {


    private final static int maxNotificationInterval = 10_000;

    private int myPort;
    private volatile Vote currentLeader;
    private LeaderElection leaderElection;

    public GatewayPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long, InetSocketAddress> peerIDtoAddress,
                                 Long gatewayId, int numberOfObservers) throws IOException {
        super(myPort, peerEpoch, id, peerIDtoAddress, gatewayId, numberOfObservers);

        this.myPort = myPort;

        setPeerState(ServerState.OBSERVER);
        this.serverLogger.log(Level.FINE, "Set state to observing");
    }

    @Override
    public void run() {
        startUDPWorkers();
        this.leaderElection = new LeaderElection(this, this.incomingMessages, this.serverLogger);
        setPeerState(ServerState.OBSERVER);
        this.serverLogger.log(Level.FINE, "Set state to observing");
        while (!this.shutdown && !this.isInterrupted()) {
            if (this.currentLeader == null) {
                this.currentLeader = this.leaderElection.lookForLeader();
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                shutdown();
                Thread.currentThread().interrupt();
            }
        }
    }



    public long getLeaderId() {
        return (this.currentLeader != null) ? this.currentLeader.getProposedLeaderID() : this.leaderElection.lookForLeader().getProposedLeaderID();
    }

    private synchronized void findLeader() {
        long retryTimeout = 200;
        while (this.currentLeader == null) {
            try {
                Message message = super.incomingMessages.poll(retryTimeout, TimeUnit.MILLISECONDS);

                if (message == null) {
                    //If no notifications received...
                    //...use exponential back-off when notifications not received but no longer than maxNotificationInterval...
                    //...resend notifications to prompt a reply from others
                    Thread.sleep(retryTimeout);
                    //retryTimeout *= 2;
                    retryTimeout = Math.min(retryTimeout * 2, maxNotificationInterval);

                    continue;

                }
                retryTimeout = 200;

                ElectionNotification electionNotification = getNotificationFromMessage(message);
                if (electionNotification.getState().equals(ServerState.FOLLOWING) || electionNotification.getState().equals(ServerState.LEADING)) {
                    this.currentLeader = new Vote(electionNotification.getProposedLeaderID(), electionNotification.getPeerEpoch());
                    break;
                }
            } catch (InterruptedException e) {
                if (this.shutdown || this.isInterrupted()) {
                    this.serverLogger.log(Level.FINE, "GatewayPeer shutting down", e);
                    return;
                }
                this.serverLogger.log(Level.WARNING, "Unusual error while listening for a leader. Trying again", e);
            }


        }
    }

    @Override
    public Vote getCurrentLeader() {
        return (this.currentLeader == null) ? null : this.currentLeader;
    }

    public GatewayPeerServerImpl getPeerServer() {
        return this;
    }

    @Override
    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        //this.shutdown = true;
        this.interrupt();
        super.shutdown();
    }

    private ElectionNotification getNotificationFromMessage(Message received) {
        byte[] contents = received.getMessageContents();
        ByteBuffer buffer = ByteBuffer.wrap(contents);
        buffer.clear();
        long proposedLeaderID = buffer.getLong();
        long senderID = buffer.getLong();
        long peerEpoch = buffer.getLong();
        PeerServer.ServerState serverState = PeerServer.ServerState.getServerState(buffer.getChar());
        return new ElectionNotification(proposedLeaderID, serverState, senderID, peerEpoch);
    }
}
