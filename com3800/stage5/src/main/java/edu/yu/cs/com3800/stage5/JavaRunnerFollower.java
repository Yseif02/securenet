package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread{
    private final PeerServer parentServer;
    //private final JavaRunner javaRunner = new JavaRunner();
    //private final Map<Long, Task> allTasks;
    Logger logger;
    private volatile boolean shutdown;
    private ServerSocket serverSocket;
    private final static int maxNotificationInterval = 10_000;

    private final LinkedBlockingQueue<byte[]> queuedCompletedWork = new LinkedBlockingQueue<>();
    private final Phaser phaser = new Phaser(1);



    public JavaRunnerFollower(PeerServerImpl parentServer) throws IOException {
        this.parentServer = parentServer;
        //this.allTasks = new HashMap<>();
        this.logger = LoggingServer.createLogger(
                "JavaRunnerFollower on " + this.parentServer.getUdpPort(),
                "JavaRunnerFollower-With-Id-" + this.parentServer.getServerId() +"-on-udpPort-" + this.parentServer.getUdpPort(), true
                );
    }


    @Override
    public void run() {
        JavaRunner javaRunner;
        javaRunner = initJavaRunner();
        if (javaRunner == null) {return;}
        this.logger.log(Level.FINE, "Initialized Java runner");
        try {
            this.serverSocket = new ServerSocket(this.parentServer.getUdpPort() + 2);
            //System.out.println("Follower " + this.parentServer.getServerId() + " waiting for work");
            this.logger.log(Level.FINE, "Follower listening on TCP port " + serverSocket.getLocalPort());
            while (!this.shutdown && !this.isInterrupted()) {
                //Socket client = null;
                Message workRequest = null;
                long requestId = -1;
                Message response = null;
                boolean finishedExecuting = false;
                InputStream codeInputStream = null;
                String code = null;
                byte[] request = null;

                try (Socket client = serverSocket.accept();) {
                    //client
                    this.logger.log(Level.FINE, "Accepted connection from " + client.getInetAddress().getHostName() + ":" + client.getPort());
                    //System.out.println("Accepted connection from leader");
                    InputStream inputStream = client.getInputStream();
                    request = inputStream.readAllBytes();
                    /*try {
                    } catch (IOException e) {
                        System.out.println("Client socket error receiving bytes from leader");
                        if (this.shutdown || Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        continue;
                    }*/


                    if (request.length == 0) {
                        continue;
                    }

                    try {
                        workRequest = new Message(request);
                    } catch (Exception e) {
                        this.logger.log(Level.WARNING, "[JRF-"+ this.parentServer.getServerId() + "]: Received malformed message, ignoring.");
                        continue;
                    }

                    // new leader
                    if (workRequest.getMessageType().equals(Message.MessageType.NEW_LEADER_GETTING_LAST_WORK)) {

                        //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Received new leader work request. Sending completed work");
                        sendOldWorkToLeader(client, workRequest);
                        continue;
                    }
                    requestId = workRequest.getRequestID();
                    this.logger.log(Level.FINER, "Parsed work request " + requestId + " from master");
                    byte[] srcBytes = workRequest.getMessageContents();
                    codeInputStream = new ByteArrayInputStream(srcBytes);

                    //System.out.println("Follower " + this.parentServer.getServerId() + " now executing work-" + requestId);

                    long start = System.currentTimeMillis();
                    String runnerResponse = null;


                    try {
                        runnerResponse = javaRunner.compileAndRun(codeInputStream);
                        if (Thread.currentThread().isInterrupted() || this.shutdown) {
                            System.out.println("[JRF-" + this.parentServer.getServerId() +  "]: Follower interrupted during execution of req " + requestId +
                                    ". Discarding result and letting leader reassign.");
                            continue;
                        }
                    } catch (IOException | ReflectiveOperationException | IllegalArgumentException e) {
                        //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Error in code while executing code. Queueing response for leader");
                        byte[] responsePayload = getErrorResponse(e, client, requestId);
                        sendErrorToLeaderOrQueue(client, requestId, responsePayload);
                        continue;
                    }

                    //System.out.println("Follower " + this.parentServer.getServerId() + " ran work-" + requestId +
                    //        " in " + (System.currentTimeMillis() - start) + "ms");
                    //System.out.println("Code executed normally");
                    response = new Message(Message.MessageType.COMPLETED_WORK, runnerResponse.getBytes(), this.parentServer.getAddress().getHostString(), this.parentServer.getUdpPort() + 2,
                            client.getInetAddress().getHostName(), client.getPort(), requestId);
                    finishedExecuting = true;
                    ByteBuffer byteBuffer = ByteBuffer.allocate(response.getNetworkPayload().length + 4);
                    byteBuffer.putInt(200);
                    byteBuffer.put(response.getNetworkPayload());
                    byte[] responsePayload = byteBuffer.array();

                    this.logger.log(Level.FINE, "[JRF-" + this.parentServer.getServerId() + "]: Code executed. Sending response back to leader");
                    try {
                        OutputStream outputStream = client.getOutputStream();
                        outputStream.write(responsePayload);
                        outputStream.flush();
                        client.shutdownOutput();

                        this.phaser.register();
                        Thread deadLeaderTimeout = getPollLeaderTimeoutThread(requestId, responsePayload);
                        deadLeaderTimeout.start();


                        //System.out.println("Work done by server-" + this.parentServer.getServerId());
                        this.logger.log(Level.FINE, "Response set to leader. \nWaiting for next work");
                    } catch (IOException e) {
                        //.println("[JRF-" + this.parentServer.getServerId() + "]: Exception thrown while sending response for req" + requestId + " back to leader");
                        this.logger.log(Level.WARNING, "Exception caught");

                        if (Thread.currentThread().isInterrupted() || this.shutdown) {
                            this.logger.log(Level.FINE, "Exception caught during shutdown. Ignoring");
                            break;
                        }

                        this.queuedCompletedWork.offer(responsePayload);


                    }
                } catch (IOException e) {
                    this.logger.log(Level.SEVERE, "Client socket error in follower " + this.parentServer.getServerId());

                    // error receiving
                    if (this.shutdown || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            //System.out.println("Server Socket error");
            this.logger.log(Level.SEVERE, "IO error while connecting to ServerSocket");
            shutdown();
        } finally {
            try {
                this.serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private Thread getPollLeaderTimeoutThread(long requestId, byte[] responsePayload) {
        Thread pollLeaderThread = new Thread(() -> {
            try {
                //poll leader periodically every half second for fail time
                long until = System.currentTimeMillis() + 10000;
                Vote leader = this.parentServer.getCurrentLeader();
                if (leader == null) {
                    this.queuedCompletedWork.offer(responsePayload);
                    //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Discovered dead leader within FAIL + 1 (10 Seconds + 1) since sending work " + requestId + " response. " +
                    //        "Queuing completed work for new Leader");
                    return;
                }
                while (System.currentTimeMillis() < until) {
                    Thread.sleep(500);
                    if (this.parentServer.isPeerDead(leader.getProposedLeaderID())) {
                        this.queuedCompletedWork.offer(responsePayload);
                        //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Discovered dead leader within FAIL + 1 (10 Seconds + 1) since sending work " + requestId + " response. " +
                        //        "Queuing completed work for new Leader");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                this.queuedCompletedWork.offer(responsePayload);
            } finally {
                phaser.arriveAndDeregister();
            }
        });
        pollLeaderThread.setDaemon(true);
        pollLeaderThread.setName("[JRF-" + this.parentServer.getServerId() + "]: Leader poller for req-" + requestId);
        return pollLeaderThread;
    }

    private void sendOldWorkToLeader(Socket client, Message request) {
        // will wait until any thread that completed work and is now waiting to see if the leader is dead to add the work to the completed work queue
        this.phaser.arriveAndAwaitAdvance();
        if (this.queuedCompletedWork.isEmpty()) {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(0);
            OutputStream outputStream = null;
            try {
                outputStream = client.getOutputStream();
                outputStream.write(buffer.array());
                outputStream.flush();
                client.shutdownOutput();
            } catch (IOException ignored) {}
            //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: No old work sending 0 to leader");
            return;
        }
        int numOldWork = this.queuedCompletedWork.size();
        if (numOldWork == 1) {
            //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: getting old work from completed response queue");
            byte[] completedWork = null;
            try {
                completedWork = this.queuedCompletedWork.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: got old work from queue");
            ByteBuffer buffer = ByteBuffer.allocate(4 + completedWork.length);
            buffer.putInt(1);
            buffer.put(completedWork);

            OutputStream outputStream = null;
            try {
                //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Attempting to send response to leader");
                outputStream = client.getOutputStream();
                outputStream.write(buffer.array());
                outputStream.flush();
                client.shutdownOutput();
                completedWork = null;
                this.queuedCompletedWork.clear();
                //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Sent Work to leader");
            } catch (IOException e) {
                //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Error connecting to leader to send response. Queueing completed work for next leader");
                // error sending old work to leader. requeue for next leader
                this.queuedCompletedWork.offer(completedWork);
            }
        } else {
            //this edge case most probably can't happen but
            int lengthOfAllWork = this.queuedCompletedWork.stream().mapToInt(arr -> arr.length).sum();
            ByteBuffer buffer = ByteBuffer.allocate(4 + (numOldWork * 4) + lengthOfAllWork);
            buffer.putInt(numOldWork);
            List<byte[]> workInOrder = new ArrayList<>();
            while (!this.queuedCompletedWork.isEmpty()) {
                byte[] work = this.queuedCompletedWork.poll();
                buffer.putInt(work.length);
                buffer.put(work);
                workInOrder.add(work);
            }

            OutputStream outputStream = null;
            try {
                outputStream = client.getOutputStream();
                outputStream.write(buffer.array());
                outputStream.flush();
                client.shutdownOutput();
                workInOrder.clear();
                workInOrder = null;
                this.queuedCompletedWork.clear();
            } catch (IOException e) {
                // error sending old work to leader. requeue for next leader
                workInOrder.forEach(this.queuedCompletedWork::offer);
            }


        }
    }

    //send the error to the leader or add to the work queue if IOE during comm with leader
    private void sendErrorToLeaderOrQueue(Socket client, long requestId, byte[] responsePayload) {
        try {
            //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Attempting to send response to leader");
            this.logger.log(Level.WARNING, "Sending Exception response back to leader");
            OutputStream outputStream = client.getOutputStream();
            outputStream.write(responsePayload);
            outputStream.flush();
            client.shutdownOutput();
            phaser.register();
            Thread deadLeaderTimeout = getPollLeaderTimeoutThread(requestId, responsePayload);
            deadLeaderTimeout.start();
            this.logger.log(Level.FINE, "Response sent to leader");
        } catch (IOException ex) {
            // error during tcp comm with leader. queue result
            //System.out.println("[JRF-" + this.parentServer.getServerId() + "]: Error connecting to leader to send response. Queueing completed work for next leader");
            this.queuedCompletedWork.offer(responsePayload);
            this.logger.log(Level.SEVERE, "IO Error sending error work " + requestId + " response back to leader", ex);
        }
    }

    private byte[] getErrorResponse(Exception e, Socket client, long requestId) {
        Message response;
        ByteArrayOutputStream stackTrace = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(stackTrace));
        String error = e.getMessage() + "\n" + stackTrace.toString();
        response = new Message(Message.MessageType.COMPLETED_WORK, error.getBytes(), this.parentServer.getAddress().getHostString(), this.parentServer.getUdpPort() + 2,
                client.getInetAddress().getHostName(), client.getPort(), requestId, true);
        ByteBuffer byteBuffer = ByteBuffer.allocate(response.getNetworkPayload().length + 4);
        byteBuffer.putInt(400);
        byteBuffer.put(response.getNetworkPayload());
        return byteBuffer.array();
    }


    private JavaRunner initJavaRunner() {
        JavaRunner javaRunner;
        try {
            javaRunner = new JavaRunner();
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "Error while initializing JavaRunner, trying again", e);
            try {
                javaRunner = new JavaRunner();
            } catch (IOException ex) {
                this.logger.log(Level.SEVERE, "Fatal error while attempting 2nd initializing. Shutting down follower", e);
                shutdown();
                return null;
            }
        }
        return javaRunner;
    }

    public void shutdown() {
        if (!this.shutdown) {
            this.shutdown = true;
            try {
                if (this.serverSocket != null) {
                    this.serverSocket.close();
                }
            } catch (IOException ignored) {}
            this.interrupt();
        }
    }


    // This method is called by a former follower who was just promoted to leader
    public LinkedBlockingQueue<byte[]> getLastCompletedWork() {
        LinkedBlockingQueue<byte[]> copy = new LinkedBlockingQueue<>(this.queuedCompletedWork);
        this.queuedCompletedWork.clear();
        return copy;
    }

}
