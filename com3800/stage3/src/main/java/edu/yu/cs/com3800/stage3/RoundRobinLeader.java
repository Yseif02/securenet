package edu.yu.cs.com3800.stage3;

import edu.yu.cs.com3800.ElectionNotification;
import edu.yu.cs.com3800.LeaderElection;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.PeerServer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoundRobinLeader extends Thread{
    private final PeerServer parentServer;
    private final LinkedBlockingQueue<Message> incomingMessages;
    private final LinkedBlockingQueue<Message> outgoingMessages;
    private final Map<Long, InetSocketAddress> peerIDtoAddress;
    private volatile boolean shutdown;
    PeerServerImpl.IdGenerator idGenerator;
    Logger logger;

    private final static int maxNotificationInterval = 10_000;

    public RoundRobinLeader(PeerServer parentServer, LinkedBlockingQueue<Message> incomingMessages,
                            LinkedBlockingQueue<Message> outgoingMessages, Map<Long, InetSocketAddress> peerIDtoAddress, PeerServerImpl.IdGenerator idGenerator, Logger logger) { //uses public IdGenerator
        //TODO: defensive programming on incomingMessages
        if (parentServer == null) { throw new IllegalArgumentException("ParentServer can't be null"); }
        this.parentServer = parentServer;
        this.incomingMessages = incomingMessages;
        this.outgoingMessages = outgoingMessages;
        this.peerIDtoAddress = peerIDtoAddress;
        this.idGenerator = idGenerator;
        this.logger = logger;
    }


    @Override
    public void run() {
        List<Long> serversById = this.peerIDtoAddress.keySet().stream().toList();
        int position = 0;
        Map<Long, Request> requests = new HashMap<>();
        while (!this.shutdown && !Thread.currentThread().isInterrupted()) {
            long retryTimeout = 200;
            while (true) {
                try {
                    Message message = this.incomingMessages.poll(retryTimeout, TimeUnit.MILLISECONDS);
                    if (message == null) {
                        Thread.sleep(retryTimeout);
                        retryTimeout = Math.min(retryTimeout * 2, maxNotificationInterval);
                        continue;
                    }
                    retryTimeout = 200;
                    //Message.MessageType messageType = getMessageType(message);
                    switch (getMessageType(message)) {
                        case WORK -> {
                            /*long requestId = message.getRequestID();
                            if (requestId == -1) {
                            }*/
                            this.logger.log(Level.FINE, "Received work request from client: " + message.getSenderHost() + ":" + message.getSenderHost());
                            long requestId = this.idGenerator.getNextRequestId();
                            //this.logger.log(Level.FINE, "Assigning work request id " + requestId);
                            long nextServerId = serversById.get(position++ % serversById.size());
                            InetSocketAddress nextServerAddress = this.peerIDtoAddress.get(nextServerId);
                            Message serverRequestMessage = new Message(
                                    Message.MessageType.WORK,
                                    message.getMessageContents(),
                                    this.parentServer.getAddress().getHostString(),
                                    this.parentServer.getUdpPort(),
                                    nextServerAddress.getHostString(),
                                    nextServerAddress.getPort(),
                                    requestId
                            );
                            logger.log(Level.FINE, "Sending work " + serverRequestMessage.getRequestID() + ", to server " + serverRequestMessage.getReceiverHost() + ":" + serverRequestMessage.getReceiverPort());
                            this.parentServer.sendMessage(Message.MessageType.WORK, serverRequestMessage.getNetworkPayload(), nextServerAddress);
                            //this.outgoingMessages.offer(serverRequestMessage);
                            Request request = new Request(message, serverRequestMessage);
                            requests.put(requestId, request);
                        }

                        case COMPLETED_WORK -> {
                            this.logger.log(Level.FINE, "Received completed work " + message.getRequestID() + " from " + "Follower at port " + message.getSenderPort());
                            long requestId = message.getRequestID();
                            Request fullRequest = requests.get(requestId);
                            if (fullRequest == null) {
                                logger.log(Level.WARNING, "Received completed work for unknown requestId " + requestId +
                                        " from " + message.getSenderHost() + ":" + message.getSenderPort());
                                break;
                            }
                            fullRequest.setWorkResponse(message);
                            String clientHost = fullRequest.clientRequest.getSenderHost();
                            int clientPort = fullRequest.clientRequest.getSenderPort();
                            Message clientResponse = new Message(
                                    Message.MessageType.COMPLETED_WORK,
                                    message.getMessageContents(),
                                    this.parentServer.getAddress().getHostString(),
                                    this.parentServer.getUdpPort(),
                                    clientHost,
                                    clientPort,
                                    requestId
                                    );
                            fullRequest.setClientResponse(clientResponse);
                            this.parentServer.sendMessage(Message.MessageType.COMPLETED_WORK, clientResponse.getNetworkPayload(), new InetSocketAddress(clientHost, clientPort)); //InetSocketAddress still may exist
                        }

                        case ELECTION -> {
                            ElectionNotification electionNotification = LeaderElection.getNotificationFromMessage(message);
                            if (electionNotification.getState().equals(PeerServer.ServerState.LOOKING)) {
                                this.parentServer.sendMessage(Message.MessageType.ELECTION,
                                        LeaderElection.buildMsgContent(
                                                new ElectionNotification(this.parentServer.getCurrentLeader().getProposedLeaderID(),
                                                                         this.parentServer.getPeerState(),
                                                                         this.parentServer.getServerId(),
                                                                         this.parentServer.getPeerEpoch())
                                                ), this.peerIDtoAddress.get(electionNotification.getSenderID()));
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    shutdown();
                    break;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Unexpected error in RoundRobinLeader", e);
                }
            }
        }

    }

    private class Request {
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
    }

    private void shutdown() {
        if (!this.shutdown) {
            this.shutdown = true;
            this.interrupt();
        }
    }
}
