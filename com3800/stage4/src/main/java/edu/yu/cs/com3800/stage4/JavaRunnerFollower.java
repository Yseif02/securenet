package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread{
    private final PeerServer parentServer;
    private final LinkedBlockingQueue<Message> incomingMessages;
    //private final JavaRunner javaRunner = new JavaRunner();
    //private final Map<Long, Task> allTasks;
    Logger logger;
    private volatile boolean shutdown;
    private ServerSocket serverSocket;
    private final static int maxNotificationInterval = 10_000;



    public JavaRunnerFollower(PeerServerImpl parentServer, LinkedBlockingQueue<Message> incomingMessages) throws IOException {
        this.parentServer = parentServer;
        this.incomingMessages = incomingMessages;
        //this.allTasks = new HashMap<>();
        this.logger = LoggingServer.createLogger(
                "JavaRunnerFollower on " + this.parentServer.getUdpPort(),
                "JavaRunnerFollower-With-Id-" + this.parentServer.getServerId() +"-on-udpPort-" + this.parentServer.getUdpPort(), true
                );
    }


    @Override
    public void run() {
        long retryTimeout = 200;
        JavaRunner javaRunner;
        javaRunner = initJavaRunner();
        if (javaRunner == null) return;
        this.logger.log(Level.FINE, "Initialized Java runner");
        try {
            this.serverSocket = new ServerSocket(this.parentServer.getUdpPort() + 2);
            this.logger.log(Level.FINE, "Follower listening on TCP port " + serverSocket.getLocalPort());
            while (!this.shutdown && !this.isInterrupted()) {
                Socket client = null;
                Message workRequest = null;
                long requestId = -1;
                try {
                    client = serverSocket.accept();
                    this.logger.log(Level.FINE, "Accepted connection from " + client.getInetAddress().getHostName() + ":" + client.getPort());
                    InputStream inputStream = client.getInputStream();
                    byte[] request = inputStream.readAllBytes();
                    workRequest = new Message(request);
                    requestId = workRequest.getRequestID();
                    this.logger.log(Level.FINER, "Parsed work request " + requestId + " from master");
                    String code = new String(workRequest.getMessageContents());
                    InputStream codeInputStream = new ByteArrayInputStream(code.getBytes());

                    String runnerResponse = javaRunner.compileAndRun(codeInputStream);
                    Message response = new Message(Message.MessageType.COMPLETED_WORK, runnerResponse.getBytes(StandardCharsets.UTF_8), this.parentServer.getAddress().getHostString(), this.parentServer.getUdpPort() + 2,
                            client.getInetAddress().getHostName(), client.getPort(), requestId);
                    ByteBuffer byteBuffer = ByteBuffer.allocate(response.getNetworkPayload().length + 4);
                    byteBuffer.putInt(200);
                    byteBuffer.put(response.getNetworkPayload());
                    byte[] responsePayload = byteBuffer.array();

                    this.logger.log(Level.FINE, "Code executed. Sending response back to leader");
                    OutputStream outputStream = client.getOutputStream();
                    outputStream.write(responsePayload);
                    outputStream.flush();
                    client.shutdownOutput();
                    this.logger.log(Level.FINE, "Response set to leader. \nWaiting for next work");
                } catch (IOException | ReflectiveOperationException e) {
                    this.logger.log(Level.WARNING, "Exception caught");
                    if (this.shutdown) {
                        this.logger.log(Level.FINE, "Exception caught during shutdown. Ignoring");
                        break;
                    }
                    //this.logger.log(Level.WARNING, "Preparing response back to leader");

                    handleException(e, client, requestId);
                }
            }
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "IO error while connecting to ServerSocket");
            shutdown();
        } finally {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleException(Exception e, Socket client, long requestId) {
        ByteArrayOutputStream stackTrace = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(stackTrace));
        String error = e.getMessage() + "\n" + stackTrace.toString();
        Message response = new Message(Message.MessageType.COMPLETED_WORK, error.getBytes(StandardCharsets.UTF_8), this.parentServer.getAddress().getHostString(), this.parentServer.getUdpPort() + 2,
                client.getInetAddress().getHostName(), client.getPort(), requestId, true);
        ByteBuffer byteBuffer = ByteBuffer.allocate(response.getNetworkPayload().length + 4);
        byteBuffer.putInt(400);
        byteBuffer.put(response.getNetworkPayload());
        byte[] responsePayload = byteBuffer.array();
        try {
            this.logger.log(Level.WARNING, "Sending Exception response back to leader");
            OutputStream outputStream = client.getOutputStream();
            outputStream.write(responsePayload);
            outputStream.flush();
            client.shutdownOutput();
            this.logger.log(Level.FINE, "Response sent to leader");
        } catch (IOException ex) {
            this.logger.log(Level.SEVERE, "IO Error sending error work " + requestId +" response back to leader", ex);
        }
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

    /*private Message.MessageType getMessageType(Message message) {
        return message.getMessageType();
    }*/

    /*private class Task {
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
    }*/

}
