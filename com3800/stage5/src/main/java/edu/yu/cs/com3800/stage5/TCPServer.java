package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TCPServer extends Thread implements LoggingServer {

    private final LinkedBlockingQueue<Message> workRequests;
    private final ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses;
    private int tcpPort;
    private String myHost;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("TCPServer-Worker-Thread-" + thread.threadId());
                return thread;
            }
    );
    private volatile boolean shutdown;
    //PeerServerImpl.IdGenerator idGenerator;
    private ServerSocket serverSocket;
    private Logger mainTcpServerLogger;
    private PeerServer parentServer;
    private long gatewayId;
    private final CountDownLatch readyForWork;
    private final ConcurrentHashMap<Long, byte[]> oldWork;


    public TCPServer (PeerServer parentServer, LinkedBlockingQueue<Message> workRequests, ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses ,
                      int tcpPort, String myHost, long gatewayId, CountDownLatch readyForWork, ConcurrentHashMap<Long, byte[]> oldWork) {
        this.workRequests = workRequests;
        this.pendingResponses = pendingResponses;
        this.tcpPort = tcpPort;
        this.myHost = myHost;
        this.parentServer = parentServer;
        this.gatewayId = gatewayId;
        try {
            this.mainTcpServerLogger = initializeLogging(this.getClass().getSimpleName() + "-On-TcpPort-" + this.tcpPort);
        } catch (IOException e) {
            this.mainTcpServerLogger = Logger.getLogger(this.getClass().getSimpleName() + "-On-TcpPort-" + this.tcpPort);
        }
        this.readyForWork = readyForWork;
        this.oldWork = oldWork;
    }

    @Override
    public void run() {
        try {
            readyForWork.await();
            this.serverSocket = new ServerSocket(tcpPort);
            while (!this.shutdown && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    //System.out.println("Accepted request from Gateway. Handing off to pool worker");
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    private void handleClient(Socket clientSocket) {
        this.mainTcpServerLogger.log(Level.FINE, "TCP connection initiated");
        long requestId = -1;
        try (clientSocket) {
            InputStream inputStream = clientSocket.getInputStream();
            byte[] bytes = Util.readAllBytesFromNetwork(inputStream);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            requestId = buffer.getLong();
            byte[] request = new byte[bytes.length - 8];
            buffer.get(request);
            this.mainTcpServerLogger.log(Level.FINE, "Accepted new TCP connection from gateway for request " + requestId);


            if (oldWork.containsKey(requestId)) {
                byte[] responseBytes = oldWork.get(requestId);
                if (responseBytes != null) {
                    this.mainTcpServerLogger.log(Level.FINE, "Received response, (" + responseBytes.length + ") bytes.\nNow sending response to gateway");
                    OutputStream outputStream = clientSocket.getOutputStream();
                    outputStream.write(responseBytes);
                    outputStream.flush();
                    this.mainTcpServerLogger.log(Level.FINE, "Response sent back to the gateway");
                    //System.out.println("Work was sent by previous leader");
                    return;
                }
            }

            Message workRequest = new Message(Message.MessageType.WORK, request, clientSocket.getInetAddress().getHostName(), clientSocket.getPort(),
                    myHost, tcpPort, requestId);

            if (this.shutdown || Thread.currentThread().isInterrupted()) {
                pendingResponses.remove(requestId);
                return;
            }
           CompletableFuture<byte[]> futureResponse =
                    pendingResponses.computeIfAbsent(requestId, id -> new CompletableFuture<>());
            //System.out.println("Adding request-" + requestId + " to work queue");
            this.workRequests.put(workRequest);
            this.mainTcpServerLogger.log(Level.FINE, "Work sent to RoundRobinLeader.\nWaiting for response...");

            try {
                //System.out.println("Waiting on response from RRL");
                byte[] response = futureResponse.get();
                //System.out.println("TCPServer Received response from RRL");

                this.mainTcpServerLogger.log(Level.FINE, "Received response, (" + response.length + ") bytes.\nNow sending response to gateway");
                OutputStream outputStream = clientSocket.getOutputStream();
                outputStream.write(response);
                outputStream.flush();
                this.mainTcpServerLogger.log(Level.FINE, "Response sent back to the gateway");
                pendingResponses.remove(requestId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.mainTcpServerLogger.log(
                        Level.FINE,
                        "TCPServer worker interrupted while waiting for response for request " + requestId,
                        e
                );
                return;
            } catch (ExecutionException e) {

            }
        } catch (IOException e) {
            //System.out.println("Error in TCPServer: Socket error between gateway request and TCPServer");
            this.mainTcpServerLogger.log(Level.WARNING, "TCP IO error for request " + requestId, e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.mainTcpServerLogger.log(Level.WARNING, "TCP Server interrupted");
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

        this.pendingResponses.forEach((id, future) -> {
            future.completeExceptionally(new CancellationException("TCPServer shutting down"));
        });
        this.pendingResponses.clear();

        this.threadPool.shutdownNow();
        this.interrupt();
    }
}
