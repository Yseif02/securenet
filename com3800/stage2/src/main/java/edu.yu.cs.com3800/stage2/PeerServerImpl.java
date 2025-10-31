package edu.yu.cs.com3800.stage2;

import edu.yu.cs.com3800.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PeerServerImpl extends Thread implements PeerServer {
    private final InetSocketAddress myAddress;
    private final int myPort;
    private ServerState state;
    private volatile boolean shutdown;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private LinkedBlockingQueue<Message> incomingMessages;
    private Long id;
    private long peerEpoch;
    //final Object voteLock = new Object();
    private volatile Vote currentLeader;
    private Map<Long, InetSocketAddress> peerIDtoAddress;
    private final LeaderElection leaderElection;
    private Logger serverLogger;


    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;

    public PeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long, InetSocketAddress> peerIDtoAddress) {
        this.id = id;
        LoggingServer serverLogger = new LoggingServer() {
            @Override
            public Logger initializeLogging(String fileNamePreface) throws IOException {
                return LoggingServer.super.initializeLogging(fileNamePreface);
            }
        };
        try {
            this.serverLogger = serverLogger.initializeLogging("Server " + this.id + " main thread logger");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.myPort = myPort;
        this.myAddress = new InetSocketAddress("localhost", myPort);
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.incomingMessages = new LinkedBlockingQueue<>();
        this.peerEpoch = peerEpoch;
        this.peerIDtoAddress = peerIDtoAddress;
        LoggingServer electionLogger = new LoggingServer() {
            @Override
            public Logger initializeLogging(String fileNamePreface) throws IOException {
                return LoggingServer.super.initializeLogging(fileNamePreface);
            }
        };
        try {
            this.leaderElection = new LeaderElection(this, this.incomingMessages, electionLogger.initializeLogging("Server " + this.id + " election logger"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.setPeerState(ServerState.LOOKING);
    }

    @Override
    public void run() {
        //step 1: create and run thread that sends broadcast messages
        //step 2: create and run thread that listens for messages sent to this server
        this.serverLogger.log(Level.FINE, "Started main thread");
        try {
            this.serverLogger.log(Level.FINE, "Creating UDP sender and receiver");
            this.receiverWorker = new UDPMessageReceiver(this.incomingMessages, this.getAddress(), this.myPort, this);
            this.receiverWorker.setDaemon(true);
            this.senderWorker = new UDPMessageSender(this.outgoingMessages, this.myPort);
            this.senderWorker.setDaemon(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.receiverWorker.start();
        this.senderWorker.start();
        this.serverLogger.log(Level.FINE, "Started worker threads");
        //step 3: main server loop
        try {
            while (!this.shutdown) {
                switch (getPeerState()) {
                    case LOOKING:
                        //start leader election, set leader to the election winner
                        this.serverLogger.log(Level.FINE, "Looking for leader");
                        Vote newLeader = this.leaderElection.lookForLeader();
                        //Vote newLeader = null;
                        /*while (newLeader == null) {
                            newLeader = this.leaderElection.lookForLeader();
                            //System.out.println(newLeader);
                        }*/
                        this.setCurrentLeader(newLeader);
                        this.serverLogger.log(Level.FINE, "Found leader: " + getCurrentLeader().getProposedLeaderID());
                        break;
                }
            }

        } catch (Exception e) {
            this.serverLogger.log(Level.SEVERE, "Fatal error in main loop of server " + this.id, e);
            shutdown();
            Thread.currentThread().interrupt();
            return;
            //code...
        }
    }

    @Override
    public void shutdown() {
        this.shutdown = true;
        if (this.senderWorker != null) this.senderWorker.shutdown();
        if (this.receiverWorker != null) this.receiverWorker.shutdown();

        try {
            if (this.senderWorker != null)  this.senderWorker.join();
            if (this.receiverWorker != null) this.receiverWorker.join();
        } catch (InterruptedException e) {
            this.serverLogger.log(Level.SEVERE, "Interrupted during shutdown: " + this.id, e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void setCurrentLeader(Vote v) throws IOException {
        this.currentLeader = v;
    }

    @Override
    public Vote getCurrentLeader() {
        return this.currentLeader;
    }

    @Override
    public void sendMessage(Message.MessageType type, byte[] messageContents, InetSocketAddress target) throws IllegalArgumentException {
        this.outgoingMessages.offer(new Message(type, messageContents, this.myAddress.getHostString(), this.myPort, target.getHostString(), target.getPort()));
    }

    @Override
    public void sendBroadcast(Message.MessageType type, byte[] messageContents) {
        for (InetSocketAddress target : this.peerIDtoAddress.values()) {
            this.outgoingMessages.offer(new Message(type, messageContents, this.myAddress.getHostString(), this.myPort, target.getHostString(), target.getPort()));
        }
    }

    @Override
    public ServerState getPeerState() {
        return this.state;
    }

    @Override
    public void setPeerState(ServerState newState) {
        this.state = newState;
    }

    @Override
    public Long getServerId() {
        return this.id;
    }

    @Override
    public long getPeerEpoch() {
        return this.peerEpoch;
    }

    @Override
    public InetSocketAddress getAddress() {
        //System.out.println(this.myAddress);
        return this.myAddress;
    }

    @Override
    public int getUdpPort() {
        return this.myPort;
    }

    @Override
    public InetSocketAddress getPeerByID(long peerId) {
        return this.peerIDtoAddress.get(peerId);
    }

    @Override
    public int getQuorumSize() {
        return this.peerIDtoAddress.size();
    }


}
