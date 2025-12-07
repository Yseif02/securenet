package edu.yu.cs.com3800.stage4;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoundRobinLeader extends Thread{
    private final PeerServer parentServer;
    private final LinkedBlockingQueue<Message> incomingMessages;
    private final LinkedBlockingQueue<Message> outgoingMessages;
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
    private PeerServerImpl.IdGenerator idGenerator;
    private Logger logger;
    private TCPServer tcpServer;
    private final AtomicInteger position = new AtomicInteger(0);
    private CopyOnWriteArrayList<Long> followersById = new CopyOnWriteArrayList<>();

    private final static int maxNotificationInterval = 10_000;

    public RoundRobinLeader(PeerServer parentServer,
                            LinkedBlockingQueue<Message> incomingMessages,
                            LinkedBlockingQueue<Message> outgoingMessages,
                            ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress,
                            PeerServerImpl.IdGenerator idGenerator,
                            CopyOnWriteArrayList<Long> followersById) { //uses public IdGenerator

        if (parentServer == null) { throw new IllegalArgumentException("ParentServer can't be null"); }
        this.parentServer = parentServer;
        this.incomingMessages = incomingMessages;
        this.outgoingMessages = outgoingMessages;
        this.peerIDtoAddress = peerIDtoAddress;
        this.idGenerator = idGenerator;
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

        LinkedBlockingQueue<Message> workRequests = new LinkedBlockingQueue<>();
        ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses = new ConcurrentHashMap<>();
        this.tcpServer = new TCPServer(workRequests, pendingResponses, this.parentServer.getUdpPort()+2, this.parentServer.getAddress().getHostName(), idGenerator);
        this.tcpServer.setDaemon(true);
        this.tcpServer.start();
        //List<Long> serversById = this.peerIDtoAddress.keySet().stream().toList();
        //serversById = Collections.synchronizedList(serversById);
        //List<Long> serversById = Collections.synchronizedList(this.peerIDtoAddress.keySet().stream().toList());
        //int position = 0;

        //Map<Long, Request> requests = new HashMap<>();
        while (!this.shutdown && !this.isInterrupted()) {
            try {
                Message workMessage = workRequests.take();
                this.logger.log(Level.FINE, "Received next work request from TCP server");
                this.threadPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        logger.log(Level.FINE, "Round robin worker thread sending work to follower");
                        /*Logger roundRobinRequestLogger;
                        try {
                            roundRobinRequestLogger = LoggingServer.createLogger(
                                    "RRLeader-" + parentServer.getServerId() + "-WorkRequest-" + workMessage.getRequestID(),
                                    "RRLeader-" + parentServer.getServerId() + "-WorkRequest-" + workMessage.getRequestID(),
                                    true

                            );
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Error creating logger for round robin thread. Using main logger");
                            roundRobinRequestLogger = logger;
                        }*/
                        int index = position.getAndIncrement() % followersById.size();
                        long nextServerId = followersById.get(index);
                        InetSocketAddress workerAddress = peerIDtoAddress.get(nextServerId);
                        logger.log(Level.FINE, "Picked next follower with serverId " + nextServerId + ". Attempting to connect to follower");
                        try(Socket clientSocket = new Socket(workerAddress.getHostString(), workerAddress.getPort() + 2)) {
                            logger.log(Level.FINE, "Connection with follower established. Sending work request");
                            OutputStream outputStream = clientSocket.getOutputStream();
                            outputStream.write(workMessage.getNetworkPayload());
                            outputStream.flush();
                            clientSocket.shutdownOutput();
                            logger.log(Level.FINE, "Work request " + workMessage.getRequestID() + " sent to follower. Waiting for response");
                            long sent = System.currentTimeMillis();

                            InputStream inputStream = clientSocket.getInputStream();
                            byte[] response = Util.readAllBytesFromNetwork(inputStream);
                            long received = System.currentTimeMillis();
                            logger.log(Level.FINE, "Received response for request " + workMessage.getRequestID() + " from follower. Time waited " + (received - sent) + "ms");
                            ByteBuffer buffer = ByteBuffer.wrap(response);
                            int statusCode = buffer.getInt();
                            byte[] messageBytes = new byte[response.length - 4];
                            buffer.get(messageBytes);
                            Message responseMessage = new Message(messageBytes);

                            //for testing
                            //roundRobinRequestLogger.log(Level.WARNING, "Response from worker: \n" + new String(responseMessage.getMessageContents()));

                            CompletableFuture<byte[]> responseFuture = pendingResponses.get(responseMessage.getRequestID());
                            responseFuture.complete(response);

                        } catch (IOException e) {
                            logger.log(Level.WARNING,
                                    "IO error while communicating with follower " + nextServerId, e);
                            logger.log(Level.WARNING, "Re-queueing work requests");
                            workRequests.offer(workMessage);
                        }
                    }
                });
            } catch (InterruptedException e) {
                this.logger.log(Level.WARNING, "Interrupted while waiting for work", e);
                shutdown();
                Thread.currentThread().interrupt();
                break;
            }

        }

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

        this.threadPool.shutdown();

        this.interrupt();
    }

    /*private class Request {
        //InetSocketAddress clientSocket;
        Message clientRequest;
        Message workRequest;
        Message workResponse;
        Message clientResponse;

        private Request(Message clientRequest, Message workRequest) {
            //this.clientSocket = clientSocket;
            this.clientRequest = clientRequest;
            this.workRequest = workRequest;
        }

        private void setWorkResponse(Message workResponse) {
            this.workResponse = workResponse;
        }

        private void setClientResponse(Message clientResponse) {
            this.clientResponse = clientResponse;
        }
    }

    private Message.MessageType getMessageType(Message message) {
        return message.getMessageType();
    }*/
}
