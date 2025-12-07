package edu.yu.cs.com3800.stage4;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayServer extends Thread implements LoggingServer {
    private final HttpServer httpServer;
    private final ExecutorService threadPool;
    private final GatewayPeerServerImpl gatewayPeerServer;
    private final ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress;
    private final ConcurrentHashMap<Integer, CachedResponse> requestCache;
    private final Logger logger;
    private long leaderId;
    private volatile boolean shutdown = false;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);



    public GatewayServer(int httpPort, int peerPort, long peerEpoch, Long serverID,
                         ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress, int numberOfObservers) throws IOException
    {
        this.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("Gateway-Worker-Thread-" + threadId());
                return thread;
            }
        );
        this.httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        createContext(this.httpServer);
        this.httpServer.setExecutor(this.threadPool);

        this.peerIDtoAddress = peerIDtoAddress;
        this.requestCache = new ConcurrentHashMap<>();
        this.logger = initializeLogging(this.getClass().getSimpleName() + "-" + peerPort);
        this.gatewayPeerServer = new GatewayPeerServerImpl(peerPort, peerEpoch, serverID, peerIDtoAddress, serverID, numberOfObservers);
        //this.gatewayPeerServer.start();
        
        /*this.httpServer.start();
        this.logger.log(Level.FINE, "Started HTTP server");*/

    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        this.httpServer.start();
        this.logger.log(Level.FINE, "Started HTTP server");
        try {
            this.shutdownLatch.await();
        } catch (InterruptedException e) {
            this.logger.log(Level.SEVERE, "Gateway Server interrupted");
            Thread.currentThread().interrupt();
        }
        this.logger.log(Level.FINE, "Exiting gateway server run");
    }

    private void createContext(HttpServer httpServer) {
        httpServer.createContext("/compileandrun", exchange -> {
            this.logger.log(Level.FINE, "Received request");
            
            if (isBadRequest(exchange)) {
                return;
            }
            byte[] requestBytes = Util.readAllBytesFromNetwork(exchange.getRequestBody());
            CachedResponse cachedResponse;
            cachedResponse = this.requestCache.get(Arrays.hashCode(requestBytes));

            if (cachedResponse != null) {
                returnCachedResponse(exchange, cachedResponse);
                return;
            }

            //cache miss
            handleRequest(exchange, requestBytes);
        });
    }

    private void handleRequest(HttpExchange exchange, byte[] requestBytes) throws IOException {
        this.logger.log(Level.FINE, "Response not cached. Sending request to TCP server");
        try(Socket clientSocket = new Socket("localhost",
                this.peerIDtoAddress.get(this.gatewayPeerServer.getCurrentLeader().getProposedLeaderID()).getPort() + 2)) {
            //send message to leader using TCP
            sendRequestToTCPServer(requestBytes, clientSocket);

            //accept response from TCPServer
            sendTCPServerResponseToClient(exchange, requestBytes, clientSocket);
        } catch (IOException e) {
             this.logger.log(Level.WARNING, "Gateway server couldn't connect to leader: " + e.getMessage());
             String errorMsg = "Leader unavailable.";
             byte[] msgBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
             exchange.sendResponseHeaders(503, msgBytes.length);
             exchange.getResponseBody().write(msgBytes);
             exchange.close();
        }
    }

    private void sendTCPServerResponseToClient(HttpExchange exchange, byte[] requestBytes, Socket clientSocket) throws IOException {
        byte[] fullResponse = Util.readAllBytesFromNetwork(clientSocket.getInputStream());
        this.logger.log(Level.FINE, "Received response from TCP Server");
        ByteBuffer buffer = ByteBuffer.wrap(fullResponse);
        int statusCode = buffer.getInt();

        //byte[] body = Arrays.copyOfRange(fullResponse, 4, fullResponse.length);
        byte[] body = new byte[fullResponse.length-4];
        buffer.get(body);
        Message responseMsg = new Message(body);
        String runnerResponse = new String(responseMsg.getMessageContents());
        //this.logger.log(Level.WARNING, "Received response from TCPServer:\n" + new String(responseMsg.getMessageContents()));
        //add to cache
        this.requestCache.put(Arrays.hashCode(requestBytes), new CachedResponse(statusCode, runnerResponse.getBytes(StandardCharsets.UTF_8)));
        this.logger.log(Level.FINE, "Cached requests-response");
        //send back to client
        exchange.getResponseHeaders().add("Cached-Response", "false");
        exchange.sendResponseHeaders(statusCode, runnerResponse.getBytes(StandardCharsets.UTF_8).length);
        exchange.getResponseBody().write(runnerResponse.getBytes());
        exchange.close();
        this.logger.log(Level.FINE, "Sent response to client");
    }

    private void sendRequestToTCPServer(byte[] requestBytes, Socket clientSocket) throws IOException {
        OutputStream outputStream = clientSocket.getOutputStream();
        outputStream.write(requestBytes);
        outputStream.flush();
        clientSocket.shutdownOutput();
        this.logger.log(Level.FINE, "Sent request to TCP Server");
    }

    private void returnCachedResponse(HttpExchange exchange, CachedResponse cachedResponse) throws IOException {
        this.logger.log(Level.FINE, "Response found  in cache, sending response");
        exchange.getResponseHeaders().add("Cached-Response", "true");
        exchange.sendResponseHeaders(cachedResponse.statusCode, cachedResponse.body.length);
        exchange.getResponseBody().write(cachedResponse.body);
        exchange.close();
        this.logger.log(Level.FINE, "Response sent");
        return;
    }

    private boolean isBadRequest(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.equals("text/x-java-source")) {
            //System.out.println(contentType);
            String response = "Content-Type must be \"text/x-java-source\"";
            sendBadResponse(exchange, 400, response);
            return true;
        }
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            String response = "Method not allowed " + exchange.getRequestMethod() + ". Only POST allowed.";
            sendBadResponse(exchange, 405, response);
            return true;
        }
        return false;
    }

    private void sendBadResponse(HttpExchange exchange, int status, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        if (response == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] message = response.getBytes(StandardCharsets.UTF_8);
        //exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, message.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(message);
        }
    }

    public GatewayPeerServerImpl getGatewayPeerServer() {
        return this.gatewayPeerServer.getPeerServer();
    }


    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        this.logger.log(Level.FINE, "Shutting down Gateway server");
        if (this.gatewayPeerServer != null) {
            this.gatewayPeerServer.shutdown();
            this.logger.log(Level.FINE, "Shut down gateway peer");
        }
        if(this.httpServer != null) {
            this.httpServer.stop(0);
            this.logger.log(Level.FINE, "http server shutdown");
        }
        if(this.threadPool != null && !this.threadPool.isShutdown()) {
            this.threadPool.shutdownNow();
            this.logger.log(Level.FINE, "threadpool shutdown");
        }
        this.shutdownLatch.countDown();
        this.logger.log(Level.FINE, "Finished shutdown");
    }


    private record CachedResponse(int statusCode, byte[] body) {
    }
}
