package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.ElectionNotification;
import edu.yu.cs.com3800.LeaderElection;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Vote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class GatewayPeerServerImpl extends PeerServerImpl implements Runnable {


    private final static int maxNotificationInterval = 10_000;

    //private volatile Vote currentLeader;
    //private LeaderElection leaderElection;


    public GatewayPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long, InetSocketAddress> peerIDtoAddress,
                                 Long gatewayId, int numberOfObservers) throws IOException {
        super(myPort, peerEpoch, id, peerIDtoAddress, gatewayId, numberOfObservers);

        setPeerState(ServerState.OBSERVER);
        this.serverLogger.log(Level.FINE, "Set state to observing");
    }

    @Override
    public void run() {
        startThreads();
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

    private void startThreads() {
        startUDPWorkers();
        LinkedBlockingQueue<Message> electionMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> gossipMessages = new LinkedBlockingQueue<>();
        Thread decoupleMessageTypesThread = getDecoupleMessageTypesThread(electionMessages, gossipMessages);
        decoupleMessageTypesThread.start();
        this.leaderElection = new LeaderElection(this, electionMessages, this.serverLogger);
        handleHeartbeats(gossipMessages, electionMessages);
    }


    @Override
    public synchronized Vote getCurrentLeader() {
        return this.currentLeader;
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

    @Override
    public void setPeerState(ServerState newState) {
        if (newState == ServerState.OBSERVER) {
            this.state = ServerState.OBSERVER;
        }
    }
}
