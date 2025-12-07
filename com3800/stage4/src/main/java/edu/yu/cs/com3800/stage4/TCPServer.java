package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TCPServer extends Thread implements LoggingServer {

    private final LinkedBlockingQueue<Message> workRequests;
    private final ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses;
    private int tcpPort;
    private String myHost;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private volatile boolean shutdown;
    PeerServerImpl.IdGenerator idGenerator;
    private ServerSocket serverSocket;
    private Logger mainTcpServerLogger;

    public TCPServer (LinkedBlockingQueue<Message> workRequests, ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses , int tcpPort, String myHost, PeerServerImpl.IdGenerator idGenerator) {
        this.workRequests = workRequests;
        this.pendingResponses = pendingResponses;
        this.tcpPort = tcpPort;
        this.myHost = myHost;
        this.idGenerator = PeerServerImpl.IdGenerator.getInstance();

        try {
            this.mainTcpServerLogger = initializeLogging(this.getClass().getSimpleName() + "-main-logger-");
        } catch (IOException e) {
            this.mainTcpServerLogger = Logger.getLogger(this.getClass().getSimpleName() + "-main-logger-");
        }

    }

    @Override
    public void run() {
        try {
            this.serverSocket = new ServerSocket(tcpPort);
            while (!this.shutdown) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                   if (this.shutdown) {
                       this.mainTcpServerLogger.log(Level.FINE, "Shutting down...");
                       break;
                   }
                    this.mainTcpServerLogger.log(Level.SEVERE, "IO Error during TCP connection", e);
                }
            }
        } catch (IOException e) {
            this.mainTcpServerLogger.log(Level.SEVERE, "Failed to start TCPServer on port " + tcpPort, e);
        }

    }

    private void handleClient(Socket clientSocket) {
        //Logger clientLogger = null;
        long requestId = idGenerator.getNextRequestId();
        this.mainTcpServerLogger.log(Level.FINE, "Accepted client request from gateway");
        this.mainTcpServerLogger.log(Level.FINE, "Assigning request id: " + requestId + " and handing off to TCP server worker logger");
       /* try {
            clientLogger = LoggingServer.createLogger(
                    "TCPWorker-" + requestId,
                    "TCPWorker-RequestId-" + requestId,
                    true);
        } catch (IOException e) {
            this.mainTcpServerLogger.log(Level.SEVERE, "Error creating per client thread logger for request " + requestId);
            clientLogger = this.mainTcpServerLogger;
        }*/

        this.mainTcpServerLogger.log(Level.FINE, "Accepted new TCP connection from gateway for request " + requestId);

        try (clientSocket) {
            InputStream inputStream = clientSocket.getInputStream();
            CompletableFuture<byte[]> futureResponse = new CompletableFuture<>();
            pendingResponses.put(requestId, futureResponse);

            Message workRequest = new Message(Message.MessageType.WORK, inputStream.readAllBytes(), clientSocket.getInetAddress().getHostName(), clientSocket.getPort(),
                    myHost, tcpPort, requestId);

            workRequests.put(workRequest);
            this.mainTcpServerLogger.log(Level.FINE, "Work sent to RoundRobinLeader.\nWaiting for response...");
            //Byte[] response = null;

            byte[] response = futureResponse.get();
            ByteBuffer buffer = ByteBuffer.wrap(response);
            int statusCode = buffer.getInt();
            byte[] messageBytes = new byte[response.length - 4];
            buffer.get(messageBytes);
            //Message responseMessage = new Message(messageBytes);
            //clientLogger.log(Level.WARNING, "Received response from Leader: \n" + new String(responseMessage.getMessageContents()));


            this.mainTcpServerLogger.log(Level.FINE, "Received response, (" + response.length + ") bytes.\nNow sending response to gateway");
            OutputStream outputStream = clientSocket.getOutputStream();
            outputStream.write(response);
            this.mainTcpServerLogger.log(Level.FINE, "Response sent back to the gateway");
        } catch (IOException e) {
            this.mainTcpServerLogger.log(Level.WARNING, "TCP IO error for request " + requestId, e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.mainTcpServerLogger.log(Level.WARNING, "TCP Server interrupted");
        }
        catch (ExecutionException e) {
            this.mainTcpServerLogger.log(Level.SEVERE, "Error completing work request " + requestId, e);
        }
    }


    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;

        try {
            if (this.serverSocket != null && !this.serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        this.threadPool.shutdown();
        this.interrupt();
    }



}
