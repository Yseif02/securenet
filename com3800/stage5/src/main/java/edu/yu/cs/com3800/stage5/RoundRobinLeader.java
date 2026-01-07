package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoundRobinLeader extends Thread{
    private final PeerServer parentServer;
    private final ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("RRL-Worker-Thread-" + thread.threadId());
                return thread;
            }
        );
    private volatile boolean shutdown;
    //private PeerServerImpl.IdGenerator idGenerator;
    private Logger logger;
    private TCPServer tcpServer;
    private final AtomicInteger position = new AtomicInteger(0);
    private CopyOnWriteArrayList<Long> followersById = new CopyOnWriteArrayList<>();

    private final static int maxNotificationInterval = 10_000;

    private final long gatewayId;
    private final ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Message> workRequests = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<Long, byte[]> oldWork = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<AbstractMap.SimpleEntry<Long, byte[]>> responseQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Long>> assignmentHistory = new ConcurrentHashMap<>();
    private Thread finishRequestThread;

    public RoundRobinLeader(PeerServer parentServer, ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress, CopyOnWriteArrayList<Long> followersById, long gatewayId) {
        if (parentServer == null) { throw new IllegalArgumentException("ParentServer can't be null"); }
        this.parentServer = parentServer;
        this.peerIDtoAddress = peerIDtoAddress;
        this.gatewayId = gatewayId;
        this.followersById = followersById;
        try {
            this.logger = LoggingServer.createLogger(
                    "RoundRobinLeader-" + parentServer.getServerId(),
                    "RoundRobinLeader-With-Id-" + parentServer.getServerId() + "-On-udpPort-" + this.parentServer.getUdpPort(),
                    true
            );
        } catch (IOException e) {
            this.logger = parentServer instanceof PeerServerImpl peerServer
                    ? peerServer.serverLogger : Logger.getLogger("Fallback logger");
        }



    }


    @Override
    public void run() {
        //AtomicBoolean readyForWork = new AtomicBoolean(false);
        CountDownLatch readyForWork = new CountDownLatch(1);

        this.tcpServer = new TCPServer(this.parentServer , this.workRequests, this.pendingResponses,
                this.parentServer.getUdpPort() + 2, this.parentServer.getAddress().getHostName(), this.gatewayId, readyForWork, oldWork);
        this.tcpServer.setDaemon(true);
        this.tcpServer.start();

        if (this.parentServer.getPeerEpoch() > 0) {
            //System.out.println("[RRL-" + this.parentServer.getServerId() + "]: New leader getting old work");
            getOldWorkFromFollowers(this.oldWork);
        }
        readyForWork.countDown();
        Thread finishRequestThread = getFinishRequestThread();
        finishRequestThread.start();
        while (!this.shutdown && !this.isInterrupted()) {
            try {
                doNextWork();
            } catch (InterruptedException e) {
                this.logger.log(Level.WARNING, "Interrupted while waiting for work", e);
                shutdown();
                Thread.currentThread().interrupt();
                break;
            }

        }

    }

    //this thread completes the future. this way only one thread is able to complete the future and no deadlock occurs
    private Thread getFinishRequestThread() {
        Runnable finishRequests = () -> {
            while (!this.shutdown && !Thread.currentThread().isInterrupted()) {
                try {
                    AbstractMap.SimpleEntry<Long, byte[]> response = this.responseQueue.take();
                    long requestId = response.getKey();
                    byte[] responseBytes = response.getValue();
                    CompletableFuture<byte[]> completableFuture =
                            pendingResponses.computeIfAbsent(requestId, id -> new CompletableFuture<>());
                    completableFuture.complete(responseBytes);
                    pendingResponses.remove(requestId);
                    //System.out.println("RRL completed req " + requestId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    shutdown();
                    break;
                }
            }
        };
        finishRequestThread = new Thread(finishRequests);
        finishRequestThread.setDaemon(true);
        finishRequestThread.setName("RRL-FinishRequest-Thread-" + this.parentServer.getServerId());
        return finishRequestThread;
    }

    private void getOldWorkFromFollowers(ConcurrentHashMap<Long, byte[]> oldWork) {
        this.logger.log(Level.FINE, "Requesting old work from followers");
        this.followersById.forEach(serverId -> {
            if (serverId == this.gatewayId) {
                return;
            }
            InetSocketAddress follower = this.peerIDtoAddress.get(serverId);
            if (follower == null) {
                return;
            }

            Message requestOldWorkMessage = new Message(Message.MessageType.NEW_LEADER_GETTING_LAST_WORK, new byte[0],
                    this.parentServer.getAddress().getHostName(), this.parentServer.getUdpPort() + 2, follower.getHostString(), follower.getPort() + 2);
            this.threadPool.submit(new Runnable() {
                @Override
                public void run() {
                    //System.out.println("[RRL-" + parentServer.getServerId() + "]: Sending old work request to follower " + serverId);
                    try(Socket clientSocket = new Socket(follower.getHostString(), follower.getPort() + 2)) {
                        try {
                            //System.out.println("[RRL-" + parentServer.getServerId() + "]: Connection established with follower " + serverId + ". Attempting send");
                            logger.log(Level.FINE, "Connection with follower " + serverId + " established. Getting completed work");
                            OutputStream outputStream = clientSocket.getOutputStream();
                            outputStream.write(requestOldWorkMessage.getNetworkPayload());
                            outputStream.flush();
                            clientSocket.shutdownOutput();
                            //System.out.println("[RRL-" + parentServer.getServerId() + "]: Send success");
                        } catch (IOException e) {
                            //System.out.println("[RRL-" + parentServer.getServerId() + "]: error connecting to follower " + serverId);
                        }

                        try {
                            //System.out.println("[RRL-" + parentServer.getServerId() + "]: Getting response from follower " + serverId);
                            InputStream inputStream = clientSocket.getInputStream();
                            byte[] response = inputStream.readAllBytes();
                            //System.out.println("[RRL-" + parentServer.getServerId() + "]: received response from follower " + serverId + ". Completing old work");
                            handleOldWork(response);
                        } catch (IOException e) {
                            //System.out.println("[RRL-" + parentServer.getServerId() + "]: Error receiving old work from follower " + serverId);
                        }
                    } catch (IOException e) {
                        //System.out.println("[RRL-" + parentServer.getServerId() + "]: Client socket error communicating with follower " + serverId);
                        return;
                        // error comm with follower \_o_/
                        //                            |
                        //                           / \
                    }
                }

                private void handleOldWork(byte[] response) {
                    ByteBuffer buffer = ByteBuffer.wrap(response);
                    int numWork = buffer.getInt();
                    if (numWork == 0) {
                        //System.out.println("[RRL-" + parentServer.getServerId() + "]: No work from " + serverId);
                        logger.log(Level.FINE, "Follower " + serverId + " had no completed work");
                        return;
                    }
                    if (numWork == 1) {
                        //System.out.println("[RRL-" + parentServer.getServerId() + "]: Follower " + serverId + " has old work. Updating responses");
                        logger.log(Level.FINE, "Follower " + serverId + " sent completed work. Updating responses");

                        int statusCode = buffer.getInt();
                        byte[] workResponse = new byte[response.length - 8];
                        updatePendingResponses(buffer, workResponse, statusCode);
                    } else {
                        logger.log(Level.FINE, "Follower " + serverId + " sent completed work. Updating responses");
                        for (int i = 0; i < numWork; i++) {
                            int lenOfWork = buffer.getInt();
                            int statusCode = buffer.getInt();
                            byte[] workResponse = new byte[lenOfWork - 4];
                            updatePendingResponses(buffer, workResponse, statusCode);
                        }
                    }
                }

                private void updatePendingResponses(ByteBuffer buffer, byte[] workResponse, int statusCode) {
                    buffer.get(workResponse);
                    Message message = new Message(workResponse);
                    long requestId = message.getRequestID();

                    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + message.getNetworkPayload().length);
                    byteBuffer.putInt(statusCode);
                    byteBuffer.put(message.getNetworkPayload());
                    oldWork.put(requestId, byteBuffer.array());
                }
            });

        });
    }

    private void doNextWork() throws InterruptedException {
        Message workMessage = this.workRequests.take();
        final long requestId = workMessage.getRequestID();
        this.logger.log(Level.FINE, "Received next work request from TCP server");
        //System.out.println("Received next work request. Handing off to next follower");
        this.threadPool.submit(new Runnable() {
            @Override
            public void run() {
                logger.log(Level.FINE, "Round robin worker thread sending work to follower");

                ServerInfo serverInfo = getNextServer();
                if (serverInfo == null) {
                    // no followers.
                    return;
                }
                logger.log(Level.FINE, "Picked next follower with serverId " + serverInfo.nextServerId() + ". Attempting to connect to follower");
                if (connectAndWorkWithFollower(serverInfo.workerAddress(), serverInfo.nextServerId())) {
                    return;
                }

                //no followers
                String err = "Error: No followers. Work canceled";
                ByteBuffer buffer = ByteBuffer.allocate(err.length() + 12);
                buffer.putInt(503);
                buffer.put(err.getBytes());
                responseQueue.offer(new AbstractMap.SimpleEntry<>(requestId, buffer.array()));
                //pendingResponses.get(workMessage.getRequestID()).deliverResponse(buffer.array());
            }


            private boolean connectAndWorkWithFollower(InetSocketAddress workerAddress, long nextServerId) {
                assignmentHistory
                        .computeIfAbsent(requestId, id -> new CopyOnWriteArrayList<>())
                        .add(nextServerId);
                //System.out.println("Connecting to follower " + nextServerId);
                try(Socket clientSocket = new Socket(workerAddress.getHostString(), workerAddress.getPort() + 2)) {
                    //System.out.println("Connection established");
                    logger.log(Level.FINE, "Connection with follower " + nextServerId + " established. Sending work request");
                    OutputStream outputStream = clientSocket.getOutputStream();
                    outputStream.write(workMessage.getNetworkPayload());
                    outputStream.flush();
                    clientSocket.shutdownOutput();
                    //System.out.println("Sent work " + workMessage.getRequestID() + " to server-" + nextServerId);
                    //System.out.println("Sent work to server-" + nextServerId);
                    logger.log(Level.FINE, "Work request " + workMessage.getRequestID() + " sent to follower. Waiting for response");
                    //long sent = System.currentTimeMillis();

                    //System.out.println("Waiting on response from follower");
                    InputStream inputStream = clientSocket.getInputStream();

                    byte[] response = inputStream.readAllBytes();
                    //System.out.println("Received response from follower");
                    // 4 bytes for status code + message

                    if (response.length < 4) {
                        throw new IOException("Partial response from follower");
                    }
                    ByteBuffer buffer = ByteBuffer.wrap(response);
                    int statusCode = buffer.getInt(); //moves pointer
                    byte[] messageBytes = new byte[response.length - 4];
                    buffer.get(messageBytes);
                    Message responseMessage;
                    try {
                        responseMessage = new Message(messageBytes);
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Malformed Message from follower", e);
                    }


                    //System.out.println("Completing responseFuture for TCPServer worker thread waiting");
                    long requestId = responseMessage.getRequestID();
                    InetSocketAddress gatewayAddress = peerIDtoAddress.get(gatewayId);
                    Message msgFromRRLToTCPServer = new Message(Message.MessageType.COMPLETED_WORK, responseMessage.getMessageContents(), parentServer.getAddress().getHostString(), parentServer.getUdpPort() + 2,
                            gatewayAddress.getHostString(), gatewayAddress.getPort() + 2, requestId, responseMessage.getErrorOccurred());
                    ByteBuffer newMsgBuffer = ByteBuffer.allocate(4 + msgFromRRLToTCPServer.getNetworkPayload().length);
                    newMsgBuffer.putInt(statusCode);
                    newMsgBuffer.put(msgFromRRLToTCPServer.getNetworkPayload());

                    responseQueue.offer(new AbstractMap.SimpleEntry<>(requestId, newMsgBuffer.array()));



                } catch (IOException e) {//
                    // connection error with follower. Maybe dead?
                    //("connection to follower disconnected during work");
                    logger.log(Level.WARNING,
                            "IO error while communicating with follower " + nextServerId, e);

                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    ServerInfo nextServer = getNextServer();
                    if (nextServer == null) {
                        return false;
                    }
                    return connectAndWorkWithFollower(nextServer.workerAddress, nextServer.nextServerId);
                }
                return true;
            }
        });
    }

    private synchronized ServerInfo getNextServer() {
        //System.out.println("Total followers: " + followersById.size());
        if (this.followersById.isEmpty()) {
            return null;
        }
        int attempts = this.followersById.size();
        for (int i = 0; i < attempts; i++) {
            int next = Math.floorMod(this.position.getAndIncrement(), this.followersById.size());
            long id = this.followersById.get(next);

            if (id == this.gatewayId || id == this.parentServer.getServerId()) {
                continue;
            }

            InetSocketAddress addr = this.peerIDtoAddress.get(id);
            if (addr == null) {
                continue;
            }

            return new ServerInfo(id, addr);
        }
        return null;
    }

    private record ServerInfo(long nextServerId, InetSocketAddress workerAddress) {
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.logger.log(Level.FINE, "Shutting down RoundRobinLeader");
        this.shutdown = true;

        if (this.tcpServer != null) {
            this.tcpServer.shutdown();
        }

        this.finishRequestThread.interrupt();

        this.threadPool.shutdown();

        this.interrupt();
    }

    // this method is called by the new leader to process work it completed as a follower
    public void processCompletedWhenFollower(LinkedBlockingQueue<byte[]> finishedWork) {
        while (!finishedWork.isEmpty()) {
            byte[] work = finishedWork.poll();
            ByteBuffer buffer = ByteBuffer.wrap(work);
            int code = buffer.getInt();
            byte[] messageBytes = new byte[work.length - 4];
            buffer.get(messageBytes);
            Message message = new Message(messageBytes);
            long requestId = message.getRequestID();
            this.oldWork.put(requestId, work);
        }
    }

    /*protected static class ResponseQueue {
        private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(1);
        public void deliverResponse(byte[] response) {
            queue.offer(response);
        }
        public byte[] getResponse() throws InterruptedException {
            return queue.take();
        }
        public boolean receivedResponse() {
            return !queue.isEmpty();
        }

        public byte[] getResponse(long timeout) {
            try {

                return queue.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

    }*/

    /*protected static class ResponseQueue {
        private final Object lock = new Object();
        private byte[] response;

        public void deliverResponse(byte[] resp) {
            if (resp == null) return;
            synchronized (lock) {
                if (response != null) return;
                response = resp;
                lock.notifyAll();
            }
        }
        public byte[] awaitResponse() throws InterruptedException {
            synchronized (lock) {
                while (response == null) {
                    lock.wait();
                }
                return response;
            }
        }

        public boolean receivedResponse() {
            synchronized (lock) {
                return response != null;
            }
        }

        public byte[] awaitResponse(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (lock) {
                while (response == null) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) return null;
                    lock.wait(remaining);
                }
                return response;
            }
        }
    }*/
}
