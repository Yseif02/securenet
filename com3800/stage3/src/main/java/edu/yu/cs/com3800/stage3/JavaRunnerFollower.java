package edu.yu.cs.com3800.stage3;

import edu.yu.cs.com3800.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread{
    private final PeerServer parentServer;
    private final LinkedBlockingQueue<Message> incomingMessages;
    //private final JavaRunner javaRunner = new JavaRunner();
    private final Map<Long, Task> allTasks;
    Logger logger;
    private volatile boolean shutdown;

    private final static int maxNotificationInterval = 10_000;



    public JavaRunnerFollower(PeerServerImpl parentServer, LinkedBlockingQueue<Message> incomingMessages, Logger logger) throws IOException {
        this.parentServer = parentServer;
        this.incomingMessages = incomingMessages;
        this.allTasks = new HashMap<>();
        this.logger = logger;
    }


    @Override
    public void run() {
        while (!this.shutdown && !Thread.currentThread().isInterrupted()) {
            long retryTimeout = 200;
            JavaRunner javaRunner;
            try {
                javaRunner = new JavaRunner();
            } catch (IOException e) {
                throw new RuntimeException("Error creating Java Runner", e);
            }
            while (true) {
                try {
                    Message message = this.incomingMessages.poll(retryTimeout, TimeUnit.MILLISECONDS);
                    if (message == null) {
                        Thread.sleep(retryTimeout);
                        retryTimeout = Math.min(retryTimeout * 2, maxNotificationInterval);
                        continue;
                    }
                    retryTimeout = 200;

                    switch (getMessageType(message)) {
                        case WORK -> {
                            Message response;
                            Task task;
                            String runnerResponse;
                            this.logger.log(Level.FINE, "Received work request " + message.getRequestID() + " from host " + message.getSenderHost() + ":" + message.getSenderPort());
                            try {
                                runnerResponse = javaRunner.compileAndRun(new ByteArrayInputStream(message.getMessageContents()));
                                response = new Message(
                                        Message.MessageType.COMPLETED_WORK,
                                        runnerResponse.getBytes(StandardCharsets.UTF_8),
                                        this.parentServer.getAddress().getHostName(),
                                        this.parentServer.getUdpPort(),
                                        message.getSenderHost(),
                                        message.getSenderPort(),
                                        message.getRequestID()
                                );
                                task = new Task(message, response, Status.COMPLETED);
                                this.allTasks.put(message.getRequestID(), task);
                            } catch (Exception e) {
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                e.printStackTrace(new PrintStream(outputStream));
                                runnerResponse = e.getMessage() + "\n" + outputStream.toString();
                                response = new Message(
                                        Message.MessageType.COMPLETED_WORK,
                                        runnerResponse.getBytes(StandardCharsets.UTF_8),
                                        this.parentServer.getAddress().getHostName(),
                                        this.parentServer.getUdpPort(),
                                        message.getSenderHost(),
                                        message.getSenderPort(),
                                        message.getRequestID(),
                                        true
                                );
                                task = new Task(message, response, Status.ERROR_OCCURRED);
                                this.allTasks.put(message.getRequestID(), task);
                            }
                            this.parentServer.sendMessage(
                                    Message.MessageType.COMPLETED_WORK,
                                    response.getNetworkPayload(),
                                    new InetSocketAddress(message.getSenderHost(), message.getSenderPort())
                            );

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
                                        ), new InetSocketAddress(message.getSenderHost(), message.getSenderPort()));//this.peerIDtoAddress.get(electionNotification.getSenderID())
                            }
                        }
                    }

                } catch (InterruptedException e) {
                    shutdown();
                    break;
                }
            }
        }
    }

    private void shutdown() {
        if (!this.shutdown) {
            this.shutdown = true;
            this.interrupt();
        }
    }

    private Message.MessageType getMessageType(Message message) {
        return message.getMessageType();
    }

    private class Task {
        Message taskFromMaster;
        Message taskResponse;
        Status status;

        private Task(Message taskFromMaster, Message taskResponse, Status status) {
            this.taskFromMaster = taskFromMaster;
            this.taskResponse = taskResponse;
            this.status = status;
        }
    }

    private enum Status {
        COMPLETED, ERROR_OCCURRED
    }

}
