package edu.yu.cs.com3800.stage5;

import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.*;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PeerServerImpl extends Thread implements PeerServer, LoggingServer {
    protected UDPMessageSender senderWorker;
    protected UDPMessageReceiver receiverWorker;

    private final InetSocketAddress myAddress;
    private final int myPort;
    protected volatile ServerState state;
    protected volatile boolean shutdown;
    protected LinkedBlockingQueue<Message> outgoingMessages;
    protected LinkedBlockingQueue<Message> incomingMessages;
    private final Long id;
    private long peerEpoch;
    //final Object voteLock = new Object();
    protected volatile Vote currentLeader;
    private final CopyOnWriteArrayList<Long> allPeers;
    private final ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress;
    private final ConcurrentHashMap<String, Long> peerAddressesToId = new ConcurrentHashMap<>();
    //private final LeaderElection leaderElection;
    protected Logger serverLogger;
    private final int numberOfObservers;
    protected LeaderElection leaderElection;


    //stage3
    private RoundRobinLeader roundRobinLeader;
    private JavaRunnerFollower javaRunnerFollower;

    //stage5
    static final int GOSSIP = 500;
    static final int FAIL = GOSSIP * 20;
    static final int CLEANUP = FAIL * 2;

    protected final LinkedBlockingQueue<Long> processFailedNodes = new LinkedBlockingQueue<>();
    protected final CopyOnWriteArrayList<Long> failedNodes = new CopyOnWriteArrayList<>();
    // map of last time that this server got an update from each other server
    protected final ConcurrentHashMap<Long, Long> heartbeats = new ConcurrentHashMap<>();
    // map of each servers last known heartbeat sequence
    protected final ConcurrentHashMap<Long, Long> heartbeatSequences = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Long, Long> cleanupMap = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Long, Long> timeOfFailedNodes = new ConcurrentHashMap<>();
    protected CopyOnWriteArrayList<Long> followersIfLeader;
    protected ConcurrentHashMap<Long, ServerState> knownStates;
    private final HttpServer httpServer;
    //private long myHeartbeatSeq;
    protected Logger summaryLogger;
    protected Logger verboseLogger;
    private final long gatewayId;
    private final AtomicInteger failedObservers = new AtomicInteger(0);
    protected LinkedBlockingQueue<Message> gossipMessages;
    protected final AtomicLong timeOfLastElection = new AtomicLong(0);
    private LinkedBlockingQueue<byte[]> finishedWork = null;
    // log filename prefaces (must match LoggingServer.createLogger arguments)
    private final String summaryLogPreface;
    private final String verboseLogPreface;


    public PeerServerImpl(int udpPort, long peerEpoch, Long serverID, Map<Long, InetSocketAddress>  peerIDtoAddress, Long gatewayID, int numberOfObservers) throws IOException {
        if (udpPort < 0) { throw new IllegalArgumentException("Port must be >= 0");}
        if (peerIDtoAddress == null) { throw new IllegalArgumentException("PeerIdToAddress is null");}
        if (serverID == null || serverID < 0) {throw new IllegalArgumentException("id must be not null and >= 0");}
        this.id = serverID;
        this.gatewayId = gatewayID;
        this.numberOfObservers = numberOfObservers;
        this.myPort = udpPort;
        this.serverLogger = initializeLogging(this.getClass().getSimpleName() + " - with id " + this.id + " on udp port " + this.getUdpPort());
        this.myAddress = new InetSocketAddress("localhost", this.myPort);;
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.incomingMessages = new LinkedBlockingQueue<>();
        this.peerEpoch = peerEpoch;
        this.peerIDtoAddress = new ConcurrentHashMap<>(peerIDtoAddress);
        long now = System.currentTimeMillis();
        this.heartbeatSequences.put(this.id, 0L);
        this.allPeers = new CopyOnWriteArrayList<>();

        this.peerIDtoAddress.forEach((id,address) -> {
            this.allPeers.add(id);
            this.heartbeats.put(id, now);
            this.heartbeatSequences.put(id, 0L);
            this.peerAddressesToId.put(address.getHostString() + ":" + address.getPort(), id);
        });
        this.allPeers.remove(this.id);
        //this.myHeartbeatSeq = 0;
        //this.leaderElection = new LeaderElection(this, this.incomingMessages, this.serverLogger);

        // stage3
        this.roundRobinLeader = null;
        this.javaRunnerFollower = null;
        this.followersIfLeader = null;

        this.summaryLogPreface = "Server " + this.id + " Summary Logger";
        this.verboseLogPreface = "Server " + this.id + " Verbose Logger";

        this.summaryLogger = LoggingServer.createLogger(this.summaryLogPreface, this.summaryLogPreface, true);
        this.verboseLogger = LoggingServer.createLogger(this.verboseLogPreface, this.verboseLogPreface, true);
        int httpPort = this.myPort + 3;
        this.httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        createSummaryLogContext(this.httpServer);
        createVerboseContext(this.httpServer);
        this.httpServer.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        }));
        this.httpServer.start();
    }

    private void createSummaryLogContext(HttpServer summaryLogGetter) {
        summaryLogGetter.createContext("/summary", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            String fileName = summaryLogPreface + "-Log.txt";
            File logsDir = findLogsDirContainingFile(fileName);

            if (logsDir == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            File logFile = new File(logsDir, fileName);

            if (!logFile.exists()) {
                byte[] response = "Summary log file not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
                return;
            }

            byte[] summaryLogs = Files.readAllBytes(logFile.toPath());
            exchange.sendResponseHeaders(200, summaryLogs.length);
            exchange.getResponseBody().write(summaryLogs);
            exchange.close();
        });
    }

    private void createVerboseContext(HttpServer summaryLogGetter) {
        summaryLogGetter.createContext("/verbose", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            }

            String fileName = verboseLogPreface + "-Log.txt";
            File logsDir = findLogsDirContainingFile(fileName);

            if (logsDir == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            File logFile = new File(logsDir, fileName);
            if (!logFile.exists()) {
                byte[] response = "Verbose log file not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            }

            byte[] verboseLogs = Files.readAllBytes(logFile.toPath());
            exchange.sendResponseHeaders(200, verboseLogs.length);
            exchange.getResponseBody().write(verboseLogs);
            exchange.close();
        });
    }

    private File findLogsDirContainingFile(String fileName) {
        File file = new File(".");
        File[] dirs = file.listFiles(nestedFile ->
                nestedFile.isDirectory() && nestedFile.getName().startsWith("logs-")
        );

        if (dirs == null || dirs.length == 0) {
            return null;
        }

        Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());

        for (File dir : dirs) {
            File candidate = new File(dir, fileName);
            if (candidate.exists() && candidate.isFile()) {
                return dir;
            }
        }

        return null;
    }

    @Override
    public void run() {
        startThreads();
        if (this.getPeerState() == null) {
            this.setPeerState(ServerState.LOOKING);
        }
        try {
            while (!this.shutdown) {
                switch (getPeerState()) {
                    case LOOKING:
                        Vote vote = lookForLeader();
                        break;
                    case LEADING:
                        if (this.javaRunnerFollower != null) {
                            this.finishedWork = this.javaRunnerFollower.getLastCompletedWork();
                            this.javaRunnerFollower.shutdown();
                            this.javaRunnerFollower.join();
                            this.javaRunnerFollower = null;
                        }

                        if (this.peerEpoch > 0) {
                            this.sendBroadcast(Message.MessageType.NEW_LEADER_GETTING_LAST_WORK, new byte[0]);
                        }


                        if (this.followersIfLeader == null) {
                            this.followersIfLeader = getFollowers();
                        }
                        if (this.roundRobinLeader == null) {
                            this.serverLogger.log(Level.FINE, "\n\n\n\n Server in leading state");
                            this.roundRobinLeader = new RoundRobinLeader(this, this.peerIDtoAddress, this.followersIfLeader, this.gatewayId);
                            this.roundRobinLeader.setDaemon(true);
                            this.serverLogger.log(Level.FINE, "Initialized new RoundRobinLeader: ", this.roundRobinLeader);
                            // process work this server completed when it was previously a follower
                            if (this.finishedWork != null) {
                                this.roundRobinLeader.processCompletedWhenFollower(this.finishedWork);
                            }
                            this.roundRobinLeader.start();

                            continue;
                        }
                        Thread.sleep(50);
                        break;

                    case FOLLOWING:
                        if (this.roundRobinLeader != null) {
                            this.roundRobinLeader.shutdown();
                            this.roundRobinLeader.join();
                            this.roundRobinLeader = null;
                        }

                        if (this.javaRunnerFollower == null) {
                            this.serverLogger.log(Level.FINE, "\n\n\n\n Server in following state");
                            this.javaRunnerFollower = new JavaRunnerFollower(this);
                            this.javaRunnerFollower.setDaemon(true);
                            this.serverLogger.log(Level.FINE, "Initialized new JavaRunnerFollower: ", this.javaRunnerFollower);
                            this.javaRunnerFollower.start();
                            continue;
                        } else if (this.javaRunnerFollower.isInterrupted()) {
                            this.serverLogger.log(Level.WARNING, "Error in JRF but still in following state. Creating new Java Runner");
                            this.javaRunnerFollower = new JavaRunnerFollower(this);
                            this.javaRunnerFollower.setDaemon(true);
                            this.serverLogger.log(Level.FINE, "Initialized new JavaRunnerFollower: ", this.javaRunnerFollower);
                            this.javaRunnerFollower.start();
                            continue;
                        }
                        Thread.sleep(50);
                        break;
                    case OBSERVER:
                        Thread.sleep(50);
                }
            }

        } catch (Exception e) {
            this.serverLogger.log(Level.SEVERE, "Fatal error in main loop of server " + this.id, e);
            shutdown();
            Thread.currentThread().interrupt();
        }
    }

    private void startThreads() {
        this.serverLogger.log(Level.FINE, "Started main thread");
        startUDPWorkers();
        this.serverLogger.log(Level.FINE, "Started worker threads");
        LinkedBlockingQueue<Message> electionMessages = new LinkedBlockingQueue<>();
        this.gossipMessages = new LinkedBlockingQueue<>();
        Thread decoupleMessageTypesThread = getDecoupleMessageTypesThread(electionMessages, gossipMessages);
        decoupleMessageTypesThread.start();
        this.leaderElection =  new LeaderElection(this, electionMessages, this.serverLogger);
        handleHeartbeats(gossipMessages, electionMessages);
    }

    /**
    * Thread for polling incoming messages and checking if they are from a dead peer and also
     * adding the message to the proper queue
     */
    protected Thread getDecoupleMessageTypesThread(LinkedBlockingQueue<Message> electionMessages, LinkedBlockingQueue<Message> gossipMessages) {
        Runnable decoupleMessageTypes = () -> {
            while (!shutdown) {
                try {
                    Message message = this.incomingMessages.poll(FAIL * 2, TimeUnit.MILLISECONDS);
                    long now = System.currentTimeMillis();
                    if (message == null) {
                        continue;
                    }

                    if (message.getMessageType().equals(Message.MessageType.ELECTION)) {
                        ElectionNotification electionNotification = LeaderElection.getNotificationFromMessage(message);
                        if (this.isPeerDead(electionNotification.getSenderID())) {
                            //ignore dead server
                            continue;
                        }

                        electionMessages.offer(message);
                    } else if (message.getMessageType().equals(Message.MessageType.GOSSIP)){
                        ByteBuffer buffer = ByteBuffer.wrap(message.getMessageContents());
                        long senderId = buffer.getLong();
                        if (this.isPeerDead(senderId)) {
                            //ignore dead server
                            continue;
                        }
                        long seqNum = buffer.getLong();
                        //this.verboseLogger.log(Level.FINE, "Received gossip message from server " + senderId + ".\tSequence number: " + seqNum + ".\tTime received: " + now);
                        gossipMessages.offer(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            }
        };
        Thread decoupleMessageTypesThread = new Thread(decoupleMessageTypes);
        decoupleMessageTypesThread.setDaemon(true);
        return decoupleMessageTypesThread;
    }

    private Vote lookForLeader() {
        //start leader election, set leader to the election winner
        this.serverLogger.log(Level.FINE, "Looking for leader");
        Vote newLeader = this.leaderElection.lookForLeader();
        if (newLeader == null) {
            this.serverLogger.log(Level.WARNING, "Election returned null");
            return null;
        } else if (this.isPeerDead(newLeader.getProposedLeaderID())) {
            return null;
        }

        this.serverLogger.log(Level.FINE, "Found leader: " + newLeader.getProposedLeaderID());
        this.gossipMessages.clear();
        this.timeOfLastElection.set(System.currentTimeMillis());
        if (this.knownStates == null) {
            this.knownStates = getKnownSates();
        }
        return newLeader;
    }

    private ConcurrentHashMap<Long, ServerState>  getKnownSates() {
        return this.leaderElection.getServerStates();
    }

    private CopyOnWriteArrayList<Long> getFollowers() {
        return new CopyOnWriteArrayList<>(this.leaderElection.getFollowers());
    }

    @Override
    public void shutdown() {
        if (this.shutdown) {
            //this.serverLogger.log(Level.WARNING, "Tried to shutdown while shutdown");
            return;
        }
        this.shutdown = true;
        if (this.senderWorker != null) this.senderWorker.shutdown();
        if (this.receiverWorker != null) this.receiverWorker.shutdown();
        if (this.roundRobinLeader != null) this.roundRobinLeader.shutdown();
        if (this.javaRunnerFollower != null) this.javaRunnerFollower.shutdown();

        try {
            if (this.senderWorker != null)  this.senderWorker.join();
            if (this.receiverWorker != null) this.receiverWorker.join();
            if (this.roundRobinLeader != null) this.roundRobinLeader.join();
            if (this.javaRunnerFollower != null) this.javaRunnerFollower.join();
        } catch (InterruptedException e) {
            this.serverLogger.log(Level.SEVERE, "Interrupted during shutdown: " + this.id, e);
            Thread.currentThread().interrupt();
        }

        this.httpServer.stop(0);
    }


    protected void handleHeartbeats(LinkedBlockingQueue<Message> gossipMessageQueue, LinkedBlockingQueue<Message> electionMessages) {
        Thread sendHeartbeatsThread = getSendHeartbeatsThread();

        Thread updateHeartbeatsThread = getUpdateHeartbeatsThread(gossipMessageQueue);

        Thread ProcessFiledNodesThreadThread = getProcessFiledNodesThread(electionMessages);


        sendHeartbeatsThread.start();
        updateHeartbeatsThread.start();
        ProcessFiledNodesThreadThread.start();
    }

    /**
     * Thread that polls the process failed nodes queue
     */
    private Thread getProcessFiledNodesThread(LinkedBlockingQueue<Message> electionMessages) {
        Runnable checkForFailedNodes = () -> {
          while (!this.shutdown) {
              Long serverId;
              try {
                  serverId = this.processFailedNodes.take();
              } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
              }


              if (this.leaderElection.getServerStates().get(serverId) == null) {
                  this.failedObservers.getAndIncrement();
              }
              this.failedNodes.add(serverId);
              this.timeOfFailedNodes.put(serverId, System.currentTimeMillis());
              this.allPeers.remove(serverId);


              //leader went down
              Vote leader = this.currentLeader;
              if (leader == null) {
                  continue;
              }
              if (serverId.equals(this.currentLeader.getProposedLeaderID())) {
                  this.currentLeader = null;
                  ServerState oldState = this.getPeerState();
                  this.peerEpoch++;
                  this.setPeerState(ServerState.LOOKING);
                  this.leaderElection = new LeaderElection(this, electionMessages, this.serverLogger);
              } else if (this.id == this.currentLeader.getProposedLeaderID()) {
                  this.followersIfLeader.remove(serverId);
              }

          }
        };
        Thread checkForFailedNodesThread = new Thread(checkForFailedNodes);
        checkForFailedNodesThread.setDaemon(true);
        return checkForFailedNodesThread;
    }

    /**
     * Thread that polls from the gossip queue and updates heartbeats
     */
    private Thread getUpdateHeartbeatsThread(LinkedBlockingQueue<Message> gossipMessageQueue) {
        Runnable updateHeartbeats = () -> {
            while (!this.shutdown) {
                if (this.getPeerState() == null || this.getPeerState().equals(ServerState.LOOKING)) {
                    continue;
                }
                try {
                    Message message = gossipMessageQueue.poll(FAIL * 2, TimeUnit.MILLISECONDS);
                    if (message == null) {
                        continue;
                    }
                    if (!message.getMessageType().equals(Message.MessageType.GOSSIP)) {
                        continue;
                    }


                    ByteBuffer buffer = ByteBuffer.wrap(message.getMessageContents());
                    long senderId = buffer.getLong();
                    if (this.isPeerDead(senderId)) {
                        continue;
                    }

                    ConcurrentHashMap<Long, Long> allHeartbeats = getHeartbeatMapFromPayload(message.getMessageContents());
                    logMessage(message, allHeartbeats, senderId);

                    allHeartbeats.forEach(1L, (serverId,seqNum) -> {
                        updateHeartbeats(serverId, seqNum, senderId);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        };
        Thread updateHeartbeatsThread = new Thread(updateHeartbeats);
        updateHeartbeatsThread.setDaemon(true);
        return updateHeartbeatsThread;
    }

    private void logMessage(Message message, Map<Long, Long> allHeartbeats, long senderId) {
        String logMsg =
                "Sender Id: " + senderId + "\n" +
                "Message contents: " + message.toString() + "\n"+
                "Heartbeat map: " + allHeartbeats.toString() + "\n" +
                "Time received: " + System.currentTimeMillis() + "\n";
        this.verboseLogger.log(Level.FINE, logMsg);
    }

    private void updateHeartbeats(Long serverId, Long seqNum, long senderId) {
        if (this.failedNodes.contains(serverId)) {
            return;
        }
        Long currentSeqNumberObj = this.heartbeatSequences.get(serverId);
        Long oldHeartbeatObj = this.heartbeats.get(serverId);
        long now = System.currentTimeMillis();
        if (currentSeqNumberObj == null || oldHeartbeatObj == null) {
            this.heartbeatSequences.put(serverId, seqNum);
            this.heartbeats.put(serverId, now);
            return;
        }

        long currentSeqNum = currentSeqNumberObj;
        long oldHeartbeat = oldHeartbeatObj;

        boolean firstSinceLastElection = now - this.timeOfLastElection.get() < FAIL;

        if (now - oldHeartbeat <= FAIL || firstSinceLastElection) {
            if (serverId != this.gatewayId && this.getPeerState() == ServerState.LEADING && this.followersIfLeader != null) {
                if (serverId != this.currentLeader.getProposedLeaderID() && !this.followersIfLeader.contains(serverId)) {
                    //System.out.println("[HB-TIMING] Adding follower from heartbeat timing window: " + serverId);
                    this.followersIfLeader.add(serverId);
                } else if (serverId != this.currentLeader.getProposedLeaderID()){
                    //System.out.println("[HB-TIMING] Duplicate follower ignored from heartbeat timing window: " + serverId);
                }
            }
            if (currentSeqNum < seqNum) {
                this.heartbeatSequences.put(serverId, seqNum);
                this.heartbeats.put(serverId, now);
                this.summaryLogger.log(Level.FINE, "[" + this.id + "]: updated " + serverId + "'s heartbeat sequence to " +
                        seqNum + " based on message from " + senderId + " at node time " + now);
            }
        // heartbeat received out of time frame. Could be dead
        } else {
            boolean inCleanup = this.cleanupMap.containsKey(serverId);
            reportFailedPeer(serverId);
            if (inCleanup && (now - oldHeartbeat > CLEANUP)) {
                long nowL = System.currentTimeMillis();
                //System.out.println("Couldn't detect heartbeat again at: " + nowL + ", time since last heartbeat: " + (now - oldHeartbeat));
            // already in cleanup but we received a higher sequence number. Ignore
            } else if (inCleanup & seqNum > currentSeqNum) {
                //this.cleanupMap.remove(serverId);
            }
            else if (!inCleanup){
                // node is dead, move to clean up map
                this.cleanupMap.put(serverId, oldHeartbeat);
                long nowL = System.currentTimeMillis();
                //System.out.println("Couldn't detect heartbeat at: " + nowL + ", Last heartbeat: " + oldHeartbeat + ", time since last heartbeat: " + (nowL - oldHeartbeat));
            }
        }
    }

    private ConcurrentHashMap<Long, Long> getHeartbeatMapFromPayload(byte[] messageContents) {
        ByteBuffer buffer = ByteBuffer.wrap(messageContents);
        //move needle over
        long senderId = buffer.getLong();
        //System.out.println("building map sent from " + senderId + " to " + this.id);
        int count = buffer.getInt();
        ConcurrentHashMap<Long,Long> heartbeatMap = new ConcurrentHashMap<>();
        for (int i = 0; i < count; i++) {
            long serverId = buffer.getLong();
            long seqNum = buffer.getLong();
            char serverStateChar = buffer.getChar();
            updateServerStates(serverStateChar, serverId);
            //System.out.println("Heartbeat: Server-" + serverId + "\tSeqNum-" + seqNum);
            heartbeatMap.put(serverId, seqNum);
        }
        return heartbeatMap;
    }

    private void updateServerStates(char serverStateChar, long serverId) {
        ServerState serverState = ServerState.getServerState(serverStateChar);
        if (isPeerDead(serverId)) {
            return;
        }
        if (serverState == null) {
            return;
        }

        if (this.knownStates == null) {
            return;
        }

        if (!this.knownStates.containsKey(serverId)) {
            this.knownStates.put(serverId, serverState);
        }
        if (this.getPeerState() == ServerState.LEADING && serverState == ServerState.FOLLOWING
                && this.followersIfLeader != null) {

            ServerState prev = this.knownStates.putIfAbsent(serverId, serverState);

            if (prev == null && !this.followersIfLeader.contains(serverId)) {
                //System.out.println("[HB] Adding follower from heartbeat state: " + serverId);
                this.followersIfLeader.add(serverId);
            } else if (this.followersIfLeader.contains(serverId)) {
                //System.out.println("[HB] Duplicate follower ignored from heartbeat state: " + serverId);
            }
        }
    }

    /**
     * Thread that sends heartbeats
     */
    private Thread getSendHeartbeatsThread() {
        Runnable sendHeartbeats = () -> {
            while (!this.shutdown) {
                if (this.getPeerState() == null || this.getPeerState().equals(ServerState.LOOKING)) {
                    continue;
                }

                byte[] payload = buildPayloadFromHeartbeatMap();
                int size = this.allPeers.size();
                if (size == 0) return;
                long receivingServerId = this.allPeers.get(ThreadLocalRandom.current().nextInt(size));
                InetSocketAddress receivingServerAddress = this.peerIDtoAddress.get(receivingServerId);
                sendMessage(Message.MessageType.GOSSIP, payload, receivingServerAddress);
                try {
                    Thread.sleep(GOSSIP);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        };
        Thread sendHeartbeatsThread = new Thread(sendHeartbeats);
        sendHeartbeatsThread.setDaemon(true);
        return sendHeartbeatsThread;
    }

    private byte[] buildPayloadFromHeartbeatMap() {
        // 8 bytes for id + 8 bytes for seq number + 2 bytes for state * num heartbeats
        // 8 bytes for server id of sender
        // 4 bytes for num entries
        int size = this.heartbeatSequences.size() * 18 + 8 + 4;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putLong(this.id);
        buffer.putInt(this.heartbeatSequences.size());
        //increment my server seqNum
        long prevSeqNum = this.heartbeatSequences.get(this.id);
        this.heartbeatSequences.computeIfPresent(this.id, (k,v) -> v + 1 );
        //System.out.println("Incremented myself " + this.id + " from " + prevSeqNum + " to " + this.heartbeatSequences.get(this.id));
        this.heartbeatSequences.forEach((serverId, seqNum) -> {
            buffer.putLong(serverId);
            buffer.putLong(seqNum);
            if (this.knownStates != null && this.knownStates.containsKey(serverId)) {
                buffer.putChar(this.knownStates.get(serverId).getChar());
            } else if (this.id.equals(serverId)) {
                buffer.putChar(this.getPeerState().getChar());
            } else {
                buffer.putChar('U');
            }
        });
        return buffer.array();
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
            sendMessage(type, messageContents, target);
            //this.outgoingMessages.offer(new Message(type, messageContents, this.myAddress.getHostString(), this.myPort, target.getHostString(), target.getPort()));
        }
    }

    @Override
    public ServerState getPeerState() {
        return this.state;
    }

    @Override
    public void setPeerState(ServerState newState) {
        ServerState oldState = this.state;
        this.state = newState;
        this.summaryLogger.log(Level.FINE, "[" + this.id + "]: switching from " + oldState + " to " + this.getPeerState());
        System.out.println("[" + this.id + "]: switching from " + oldState + " to " + this.getPeerState());
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
        int totalNodes = this.peerAddressesToId.size();
        int observers = this.numberOfObservers;
        int failedNodes = this.failedNodes.size();
        int failedObservers = this.failedObservers.get();

        int voters = totalNodes - observers - failedNodes + failedObservers;

        return (voters / 2) + 1;
    }

    protected void startUDPWorkers() {
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
    }

    public boolean isShutdown() {
        return this.shutdown;
    }

    @Override
    public void reportFailedPeer(long peerID){
        if (peerID == this.id) {
            return;
        }
        if (!this.failedNodes.contains(peerID)) {
            this.knownStates.remove(peerID);
            this.failedNodes.add(peerID);
            this.summaryLogger.log(Level.SEVERE, "[" + this.id + "]: no heartbeat from server " + peerID + " - SERVER FAILED");
            System.out.println("[" + this.id + "]: no heartbeat from server " + peerID + " - SERVER FAILED");
        }
        this.processFailedNodes.offer(peerID);
    }

    @Override
    public boolean isPeerDead(long peerID){
        return this.failedNodes.contains(peerID);
    }

    @Override
    public boolean isPeerDead(InetSocketAddress address){
        String host = address.getHostString() + ":" + address.getPort();
        long id = this.peerAddressesToId.get(host);
        return isPeerDead(id);
    }
}
