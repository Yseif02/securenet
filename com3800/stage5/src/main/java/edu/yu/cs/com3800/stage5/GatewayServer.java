package edu.yu.cs.com3800.stage5;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayServer extends Thread implements LoggingServer {
    private final HttpServer httpServer;
    private final ExecutorService httpServerThreadPool;
    private final ExecutorService gatewayServerThreadPool;
    private final GatewayPeerServerImpl gatewayPeerServer;
    private final ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress;
    private final ConcurrentHashMap<Integer, CachedResponse> requestCache;
    private final Logger logger;
    //private long leaderId;
    private volatile boolean shutdown = false;
    //private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    //stage5
    private final LinkedBlockingQueue<byte[]> queuedRequests = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<Long, CompletableFuture<byte[]>> clientRequests = new ConcurrentHashMap<>();
    private AtomicLong requestIds;


    public GatewayServer(int httpPort, int peerPort, long peerEpoch, Long serverID,
                         ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress, int numberOfObservers) throws IOException
    {
        this.httpServerThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("HTTPServer-Worker-Thread-" + threadId());
                return thread;
            }
        );
        this.httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        createContext(this.httpServer);
        this.httpServer.setExecutor(this.httpServerThreadPool);

        this.gatewayServerThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("GatewayServer-Worker-Thread-" + threadId());
                return thread;
            }
        );

        this.peerIDtoAddress = peerIDtoAddress;
        this.requestCache = new ConcurrentHashMap<>();
        this.logger = initializeLogging(this.getClass().getSimpleName() + "- on port -" + peerPort);
        this.gatewayPeerServer = new GatewayPeerServerImpl(peerPort, peerEpoch, serverID, peerIDtoAddress, serverID, numberOfObservers);
        this.gatewayPeerServer.start();

        this.requestIds = new AtomicLong(1);
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
        while (!shutdown && !Thread.currentThread().isInterrupted()) {
            try {
                while (this.gatewayPeerServer.getCurrentLeader() != null) {
                    byte[] request = this.queuedRequests.poll();
                    if (request == null) {
                        Thread.sleep(5);
                        continue;
                    }

                    this.gatewayServerThreadPool.submit(() -> {
                        //System.out.println("Gateway processing next req");
                        if (this.shutdown || Thread.currentThread().isInterrupted()) {
                            return;
                        }

                        if (this.gatewayPeerServer.getCurrentLeader() == null) {
                            this.queuedRequests.offer(request);
                            return;
                        }
                        boolean sent = sendQueuedRequest(request);
                        if (!sent) {
                            this.queuedRequests.offer(request);
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                }

                Thread.sleep(5);
            } catch (InterruptedException e) {
                this.logger.log(Level.SEVERE, "Gateway Server interrupted");
                Thread.currentThread().interrupt();
                break;
            }
            this.logger.log(Level.FINE, "Exiting gateway server run");
        }
    }

    private boolean sendQueuedRequest(byte[] request) {
        if (this.shutdown || Thread.currentThread().isInterrupted() || this.gatewayPeerServer.getCurrentLeader() == null) {
            return false;
        }

        try (Socket clientSocket = new Socket("localhost",
                this.peerIDtoAddress.get(this.gatewayPeerServer.currentLeader.getProposedLeaderID()).getPort() + 2)) {
            try {
                System.out.println("Gateway sending request to TCP Server");
                sendRequestToTCPServer(request, clientSocket);
            } catch (IOException e) {
                //IO error while sending request to leader. Re-queue request
                return false;
            }

            //accept response from TCPServer
            getResponseFromTCPServer(request, clientSocket);

            return true;
        } catch (IOException e) {
            //IO error creating socket. Return false for re-queue request
            return false;
        }
    }

    private void createContext(HttpServer httpServer) {
        httpServer.createContext("/compileandrun", exchange -> {
            this.logger.log(Level.FINE, "Received request from client");
            
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
            ByteBuffer buffer = ByteBuffer.allocate(requestBytes.length + 8);
            long requestId = this.requestIds.getAndIncrement();
            buffer.putLong(requestId);
            buffer.put(requestBytes);
            //8 bytes for reqId + request
            System.out.println("Gateway handling request-" + requestId);
            handleRequest(exchange, buffer.array(), requestId);
        });
    }

    private void handleRequest(HttpExchange exchange, byte[] requestBytes, long requestId) {
        this.logger.log(Level.FINE, "Response not cached. Sending request to TCP server");
        CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();
        this.clientRequests.put(requestId, responseFuture);
        System.out.println("Gateway queued request");
        this.queuedRequests.offer(requestBytes);

        responseFuture.whenComplete((resp, ex) ->{
            System.out.println("Gateway completed future for req " + requestId + ". Now sending response to client");
            if (ex != null) {
                String err = "Gateway raised execution error when getting response for requestId " + requestId;
                try {
                    exchange.sendResponseHeaders(503, err.length());
                    exchange.getResponseBody().write(err.getBytes());
                    exchange.close();
                } catch (IOException ignored) {}
                return;
            }
            this.logger.log(Level.FINE, "Received response from TCP Server");
            ByteBuffer buffer = ByteBuffer.wrap(resp);
            int statusCode = buffer.getInt();

            //byte[] body = Arrays.copyOfRange(fullResponse, 4, fullResponse.length);
            byte[] body = new byte[resp.length-4];
            buffer.get(body);
            Message responseMsg = new Message(body);
            String runnerResponse = new String(responseMsg.getMessageContents());


            //send back to client
            exchange.getResponseHeaders().add("Cached-Response", "false");
            try {
                exchange.sendResponseHeaders(statusCode, runnerResponse.getBytes(StandardCharsets.UTF_8).length);
                exchange.getResponseBody().write(runnerResponse.getBytes());
                this.logger.log(Level.FINE, "Sent response to client");
                System.out.println("Gateway sent response for req" + requestId + " to client");
            } catch (IOException ignored) {
            } finally {
                exchange.close();
                clientRequests.remove(requestId);
                System.out.println("gateway finished req " + requestId);
            }
        });
        //this.clientRequests.remove(requestId);
    }


    // only throws IOE when IOE during comm with client
    private void getResponseFromTCPServer(byte[] requestBytes, Socket clientSocket) {

        byte[] fullResponse = null;
        ByteBuffer buffer;
        long requestId;
        try {
            buffer = ByteBuffer.wrap(requestBytes);
            requestId = buffer.getLong();
            fullResponse = clientSocket.getInputStream().readAllBytes();

            if (fullResponse.length < 4) {
                System.out.println("Gateway received invalid/empty TCP response for req " + requestId + ", re-queuing");
                this.queuedRequests.offer(requestBytes);
                return;
            }

            System.out.println("Gateway received TCPServer response for req " + requestId);
        } catch (IOException e) {
            //IO error reading response from Leader
            System.out.println("huh?");
            this.queuedRequests.offer(requestBytes);
            return;
        }
        if (this.gatewayPeerServer.isPeerDead(this.gatewayPeerServer.currentLeader.getProposedLeaderID())) {
            System.out.println("huh?");
            this.logger.log(Level.WARNING, "Received response from dead leader. re-queueing request");
            this.queuedRequests.offer(requestBytes);
            return;
        }


        System.out.println("Attempting to complete request " + requestId +  " future");
        CompletableFuture<byte[]> future = this.clientRequests.get(requestId);
        if (future != null && !future.isDone()) {
            future.complete(fullResponse);
        }
        System.out.println("Gateway completed future for req " + requestId);

        //add to cache
        addToCache(requestBytes, fullResponse);

    }

    private void addToCache(byte[] requestBytes, byte[] fullResponse) {
        try {
            System.out.println("adding to cache");
            byte[] clientRequest = new byte[requestBytes.length - 8];
            ByteBuffer clientReqBuf = ByteBuffer.wrap(requestBytes);
            clientReqBuf.getLong();
            clientReqBuf.get(clientRequest);

            ByteBuffer responseBuffer = ByteBuffer.wrap(fullResponse);
            int statusCode = responseBuffer.getInt();
            byte[] body = new byte[fullResponse.length - 4];
            responseBuffer.get(body);
            Message responseMsg = new Message(body);
            String runnerResponse = new String(responseMsg.getMessageContents());

            this.requestCache.put(Arrays.hashCode(clientRequest), new CachedResponse(statusCode, runnerResponse.getBytes(StandardCharsets.UTF_8)));
            this.logger.log(Level.FINE, "Cached requests-response");
            System.out.println("added to cache");
        } catch (Exception e) {
            this.logger.log(Level.WARNING, "Failed to cache response", e);
        }
    }

    private void sendRequestToTCPServer(byte[] requestBytes, Socket clientSocket) throws IOException {
        OutputStream outputStream = clientSocket.getOutputStream();
        outputStream.write(requestBytes);
        outputStream.flush();
        clientSocket.shutdownOutput();
        this.logger.log(Level.FINE, "Sent request to TCP Server");
        System.out.println("Sent request to TCP Server");
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

    public GatewayPeerServerImpl getPeerServer() {
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
        if (this.gatewayServerThreadPool != null && !this.gatewayServerThreadPool.isShutdown()) {
            this.gatewayServerThreadPool.shutdownNow();
            this.logger.log(Level.FINE, "gateway worker threadpool shutdown");
        }

        if (this.httpServerThreadPool != null && !this.httpServerThreadPool.isShutdown()) {
            this.httpServerThreadPool.shutdownNow();
            this.logger.log(Level.FINE, "http threadpool shutdown");
        }


        this.clientRequests.forEach((id, future) ->
                future.completeExceptionally(
                        new CancellationException("Gateway shutting down")
                )
        );
        this.clientRequests.clear();
        this.queuedRequests.clear();


        this.interrupt();

        //this.shutdownLatch.countDown();
        this.logger.log(Level.FINE, "Finished shutdown");
    }


    private record CachedResponse(int statusCode, byte[] body) {
    }


}
