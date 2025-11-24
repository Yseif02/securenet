package edu.yu.cs.com3800.stage3;

import edu.yu.cs.com3800.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PeerServerImpl extends Thread implements PeerServer {
    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;

    private final InetSocketAddress myAddress;
    private final int myPort;
    private volatile ServerState state;
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

    //stage3
    private RoundRobinLeader roundRobinLeader;
    private JavaRunnerFollower javaRunnerFollower;


    public PeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long, InetSocketAddress> peerIDtoAddress) {
        if (myPort < 0) { throw new IllegalArgumentException("Port must be >= 0");}
        if (peerIDtoAddress == null) { throw new IllegalArgumentException("PeerIdToAddress is null");}
        if (id == null || id < 0) {throw new IllegalArgumentException("id must be not null and >= 0");}
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
        InetSocketAddress tempAddress;
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            tempAddress= new InetSocketAddress(host, myPort);
        } catch (UnknownHostException e) {
            tempAddress = new InetSocketAddress("localhost", myPort);
        }
        //this.myAddress = tempAddress;
        this.myAddress = new InetSocketAddress("localhost", myPort);;
        //System.out.println("Server " + id + " binding on " + this.myAddress);
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.incomingMessages = new LinkedBlockingQueue<>();
        this.peerEpoch = peerEpoch;
        this.peerIDtoAddress = peerIDtoAddress;
        LoggingServer electionLogger = new LoggingServer() {
            @Override
            public Logger initializeLogging(String fileNamePreface) throws IOException {
                //String preface = fileNamePreface + File.separatorChar + "logs";
                return LoggingServer.super.initializeLogging(fileNamePreface);
            }
        };
        LeaderElection tempLE;
        /*try {
            tempLE = new LeaderElection(this, this.incomingMessages, electionLogger.initializeLogging("Server " + this.id + " election logger"));
        } catch (IOException e) {
            this.serverLogger.log(Level.WARNING, "Error creating election logger", e);
            Logger logger = Logger.getLogger("Server " + this.id + " election logger");
            tempLE = new LeaderElection(this, this.incomingMessages, logger);
            //throw new RuntimeException(e);
        }*/
        //this.leaderElection = tempLE;
        this.leaderElection = new LeaderElection(this, this.incomingMessages, this.serverLogger);

        // stage3
        this.roundRobinLeader = null;
        this.javaRunnerFollower = null;

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
                        if (newLeader != null) {
                            this.serverLogger.log(Level.FINE, "Found leader: " + newLeader.getProposedLeaderID());
                        } else {
                            this.serverLogger.log(Level.WARNING, "Election returned null");
                        }
                        //this.setCurrentLeader(newLeader);
                        //this.serverLogger.log(Level.FINE, "Found leader: " + getCurrentLeader().getProposedLeaderID());
                        break;                                                                                                          //In this stage using a clone of peerIDs may work but for
                                                                                                                                        //future stages where a peer goes down, I should probably
                    case LEADING:                                                                                                 //use the actual mutable map.
                        if (this.javaRunnerFollower != null) {
                            this.javaRunnerFollower.interrupt();
                            this.javaRunnerFollower = null;
                        }
                        if (this.roundRobinLeader == null) {
                            this.serverLogger.log(Level.FINE, "\n\n\n\n Server in leading state");
                            this.roundRobinLeader = new RoundRobinLeader(this, this.incomingMessages, this.outgoingMessages,
                                    new HashMap<>(this.peerIDtoAddress), IdGenerator.getInstance(), this.serverLogger);
                            this.serverLogger.log(Level.FINE, "Initialized new RoundRobinLeader: ", this.roundRobinLeader);
                            this.roundRobinLeader.start();
                            continue;
                        }
                        Thread.sleep(50);
                        break;

                    case FOLLOWING:
                        if (this.roundRobinLeader != null) {
                            this.roundRobinLeader.interrupt();
                            this.roundRobinLeader = null;
                        }
                        if (this.javaRunnerFollower == null) {
                            this.serverLogger.log(Level.FINE, "\n\n\n\n Server in following state");
                            this.javaRunnerFollower = new JavaRunnerFollower(this, this.incomingMessages, this.serverLogger);
                            this.serverLogger.log(Level.FINE, "Initialized new JavaRunnerFollower: ", this.javaRunnerFollower);
                            this.javaRunnerFollower.start();
                            continue;
                        }
                        Thread.sleep(50);
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
        if (this.shutdown) {
            this.serverLogger.log(Level.WARNING, "Tried to shutdown while shutdown");
            return;
        }
        this.shutdown = true;
        if (this.senderWorker != null) this.senderWorker.shutdown();
        if (this.receiverWorker != null) this.receiverWorker.shutdown();
        if (this.roundRobinLeader != null) this.roundRobinLeader.interrupt();
        if (this.javaRunnerFollower != null) this.javaRunnerFollower.interrupt();

        try {
            if (this.senderWorker != null)  this.senderWorker.join();
            if (this.receiverWorker != null) this.receiverWorker.join();
            if (this.roundRobinLeader != null) this.roundRobinLeader.join();
            if (this.javaRunnerFollower != null) this.javaRunnerFollower.join();
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
        switch (type) {
            case WORK, COMPLETED_WORK -> {
                this.outgoingMessages.offer(new Message(messageContents));
                break;
                //return;
            }

            case ELECTION -> {
                // The messageContents is not in full message in this case
                this.outgoingMessages.offer(new Message(type, messageContents, this.myAddress.getHostString(), this.myPort, target.getHostString(), target.getPort()));
                break;
                //return;
            }

        }
        /*if (type.equals(Message.MessageType.COMPLETED_WORK) || type.equals(Message.MessageType.WORK)) {
            this.outgoingMessages.offer(new Message(messageContents));
            return;
        }*/
        //this.outgoingMessages.offer(new Message(type, messageContents, this.myAddress.getHostString(), this.myPort, target.getHostString(), target.getPort()));
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
        return this.peerIDtoAddress.size() / 2 + 1;
    }


    public static class IdGenerator {
        private static final IdGenerator INSTANCE = new IdGenerator();
        private long requestId = 0;

        private IdGenerator() {}

        public static IdGenerator getInstance() {
            return INSTANCE;
        }

        public long getNextRequestId() {
            return ++requestId;
        }
    }


}
