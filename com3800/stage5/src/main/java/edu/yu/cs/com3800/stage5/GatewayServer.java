package edu.yu.cs.com3800.stage5;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

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
        createWorkRequestContext(this.httpServer);
        createGetLeaderAndRolesContext(this.httpServer);
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
        this.logger.log(FINE, "Started HTTP server");
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
            //this.logger.log(FINE, "Exiting gateway server run");
        }
    }

    private boolean sendQueuedRequest(byte[] request) {
        Vote currentLeader = this.gatewayPeerServer.getCurrentLeader();
        if (this.shutdown || Thread.currentThread().isInterrupted() || currentLeader == null) {
            return false;
        }
        long currentEpoch = currentLeader.getPeerEpoch();

        try (Socket clientSocket = new Socket("localhost",
                this.peerIDtoAddress.get(currentLeader.getProposedLeaderID()).getPort() + 2)) {
            try {
                //System.out.println("Gateway sending request to TCP Server");
                sendRequestToTCPServer(request, clientSocket);
            } catch (IOException e) {
                //IO error while sending request to leader. Re-queue request
                return false;
            }

            //accept response from TCPServer
            getResponseFromTCPServer(request, clientSocket, currentLeader, currentEpoch);

            return true;
        } catch (IOException e) {
            //IO error creating socket. Return false for re-queue request
            return false;
        }
    }

    private void createWorkRequestContext(HttpServer httpServer) {
        httpServer.createContext("/compileandrun", exchange -> {
            this.logger.log(FINE, "Received request from client");
            
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
            //System.out.println("Gateway handling request-" + requestId);
            handleRequest(exchange, buffer.array(), requestId, requestBytes);
        });
    }

    private void createGetLeaderAndRolesContext(HttpServer httpServer) {
        httpServer.createContext("/leader", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendBadResponse(exchange, 405,
                        "Method not allowed " + exchange.getRequestMethod());
                return;
            }

            Vote leader = this.gatewayPeerServer.getCurrentLeader();
            if (leader == null) {
                exchange.sendResponseHeaders(200, "null".length());
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write("null".getBytes());
                }
                return;
            }

            waitForKnownStates();

            String listOfIdsAndStates =
                    buildList(leader.getProposedLeaderID());

            exchange.sendResponseHeaders(200, listOfIdsAndStates.length());
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(listOfIdsAndStates.getBytes());
            }
        });
    }

    private void waitForKnownStates() {
        final int expected =
                this.peerIDtoAddress.size() - 1;
        final long timeoutMs = 5000;
        final long start = System.currentTimeMillis();

        while (true) {
            Map<Long, PeerServer.ServerState> states =
                    this.gatewayPeerServer.knownStates;

            if (states != null && states.size() >= expected) {
                return;
            }

            if (System.currentTimeMillis() - start > timeoutMs) {
                //this.logger.log(Level.WARNING, "Timed out waiting for all knownStates");
                return;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String buildList(long leader) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("{");
        stringBuilder.append("\"leader\":").append(leader).append(",");
        stringBuilder.append("\"states\":[");

        Map<Long, PeerServer.ServerState> states = this.gatewayPeerServer.knownStates;
        if (states == null) {
            stringBuilder.append("]}");
            return stringBuilder.toString();
        }

        final boolean[] first = {true};

        PeerServer.ServerState leaderState = states.get(leader);
        if (leaderState != null) {
            stringBuilder.append("{\"serverId\":").append(leader)
                    .append(",\"role\":\"").append(leaderState.name()).append("\"}");
            first[0] = false;
        }

        states.forEach((serverId, serverState) -> {
            if ((serverState == PeerServer.ServerState.OBSERVER) ||
                (gatewayPeerServer.isPeerDead(serverId)) ||
                (serverId == leader)){
                return;
            }

            if (!first[0]) stringBuilder.append(",");

            stringBuilder.append("{\"serverId\":")
                    .append(serverId)
                    .append(",\"role\":\"")
                    .append(serverState.name())
                    .append("\"}");

            first[0] = false;
        });

        stringBuilder.append("]}");
        return stringBuilder.toString();
    }


    private void handleRequest(HttpExchange exchange, byte[] requestBytes, long requestId, byte[] fullRequestNoReqId) {
        this.logger.log(FINE, "Response not cached. Sending request to TCP server");
        CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();
        this.clientRequests.put(requestId, responseFuture);
        this.logger.log(FINE,"Gateway queued request");
        this.queuedRequests.offer(requestBytes);

        responseFuture.whenComplete((resp, ex) ->{
            //System.out.println("Gateway completed future for req " + requestId + ". Now sending response to client");
            this.logger.log(FINE, "Gateway completed future for req " + requestId + ". Now sending response to client");
            if (ex != null) {
                String err = "Gateway raised execution error when getting response for requestId " + requestId;
                try {
                    exchange.sendResponseHeaders(503, err.length());
                    exchange.getResponseBody().write(err.getBytes());
                    exchange.close();
                } catch (IOException ignored) {}
                return;
            }
            //this.logger.log(Level.FINE, "Received response from TCP Server");
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
                this.logger.log(FINE, "Sent response for request " + requestId + " to client");
                //System.out.println("Gateway sent response for req" + requestId + " to client");
            } catch (IOException ignored) {
            } finally {
                exchange.close();
                clientRequests.remove(requestId);
                //System.out.println("gateway finished req " + requestId);
                this.logger.log(FINE, "Gateway finished req " + requestId);
            }
        });
        //this.clientRequests.remove(requestId);
    }


    private void getResponseFromTCPServer(byte[] requestBytes, Socket clientSocket, Vote leaderWhenSent, long epochWhenSent) {
        byte[] fullResponse = null;
        ByteBuffer buffer;
        long requestId;
        long timeReceived;
        try {
            buffer = ByteBuffer.wrap(requestBytes);
            requestId = buffer.getLong();
            fullResponse = clientSocket.getInputStream().readAllBytes();
            timeReceived = System.currentTimeMillis();
            if (fullResponse.length < 4) {
                //System.out.println("Gateway received invalid/empty TCP response for req " + requestId + ", re-queuing");
                this.logger.log(Level.WARNING, "Gateway received invalid/empty TCP response for req " + requestId + ", re-queuing");
                this.queuedRequests.offer(requestBytes);
                return;
            }

            //System.out.println("Gateway received TCPServer response for req " + requestId);
            this.logger.log(FINE, "Gateway received TCPServer response for req " + requestId);
        } catch (IOException e) {
            //IO error reading response from Leader
            this.logger.log(WARNING,"IO error in gateway while waiting/receiving response from TCPServer re-queuing request");
            this.queuedRequests.offer(requestBytes);
            return;
        }

        //need to extract sender id from message to check if dead
        /*ByteBuffer responseBuffer = ByteBuffer.wrap(fullResponse);
        int statusCode = responseBuffer.getInt();
        byte[] messageBytes = new byte[fullResponse.length - 4];
        responseBuffer.get(messageBytes);
        Message responseMsg = new Message(messageBytes);
        InetSocketAddress senderAddress = new InetSocketAddress(responseMsg.getSenderHost(), responseMsg.getSenderPort() - 2);*/

        Vote currentLeader = gatewayPeerServer.getCurrentLeader();



        if (currentLeader == null ||
                currentLeader.getPeerEpoch() > epochWhenSent ||
                (this.gatewayPeerServer.isPeerDead(leaderWhenSent.getProposedLeaderID()) && timeReceived > this.gatewayPeerServer.timeOfFailedNodes.get(leaderWhenSent.getProposedLeaderID()))) {
            this.queuedRequests.offer(requestBytes);
            this.logger.log(Level.WARNING, "Received response from dead leader. re-queueing request");
            //System.out.println("Received response from dead leader. re-queueing request");
            return;
        }



        //System.out.println("Attempting to complete request " + requestId +  " future");
        CompletableFuture<byte[]> future = this.clientRequests.get(requestId);
        if (future != null && !future.isDone()) {
            future.complete(fullResponse);
        }
        //System.out.println("Gateway completed future for req " + requestId);
        this.logger.log(FINE, "Gateway completed future for req " + requestId);

        //add to cache
        byte[] clientRequest = new byte[requestBytes.length - 8];
        ByteBuffer.wrap(requestBytes).getLong();
        ByteBuffer.wrap(requestBytes, 8, clientRequest.length).get(clientRequest);

        addToCache(clientRequest, fullResponse);

    }


    private void addToCache(byte[] clientRequest, byte[] fullResponse) {
        try {
            ByteBuffer responseBuffer = ByteBuffer.wrap(fullResponse);
            int statusCode = responseBuffer.getInt();
            byte[] body = new byte[fullResponse.length - 4];
            responseBuffer.get(body);

            Message responseMsg = new Message(body);
            byte[] runnerResponse = responseMsg.getMessageContents();

            this.requestCache.put(
                    Arrays.hashCode(clientRequest),
                    new CachedResponse(statusCode, runnerResponse)
            );

            this.logger.log(FINE, "Cached request-response");
        } catch (Exception e) {
            this.logger.log(Level.WARNING, "Failed to cache response", e);
        }
    }

    private void sendRequestToTCPServer(byte[] requestBytes, Socket clientSocket) throws IOException {
        OutputStream outputStream = clientSocket.getOutputStream();
        outputStream.write(requestBytes);
        outputStream.flush();
        clientSocket.shutdownOutput();
        this.logger.log(FINE, "Sent request to TCP Server");
        //System.out.println("Sent request to TCP Server");
    }

    private void returnCachedResponse(HttpExchange exchange, CachedResponse cachedResponse) throws IOException {
        this.logger.log(FINE, "Response found  in cache, sending response");
        exchange.getResponseHeaders().add("Cached-Response", "true");
        exchange.sendResponseHeaders(cachedResponse.statusCode, cachedResponse.body.length);
        exchange.getResponseBody().write(cachedResponse.body);
        exchange.close();
        this.logger.log(FINE, "Response sent");

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
        this.logger.log(FINE, "Shutting down Gateway server");
        if (this.gatewayPeerServer != null) {
            this.gatewayPeerServer.shutdown();
            this.logger.log(FINE, "Shut down gateway peer");
        }
        if(this.httpServer != null) {
            this.httpServer.stop(0);
            this.logger.log(FINE, "http server shutdown");
        }
        if (this.gatewayServerThreadPool != null && !this.gatewayServerThreadPool.isShutdown()) {
            this.gatewayServerThreadPool.shutdownNow();
            this.logger.log(FINE, "gateway worker threadpool shutdown");
        }

        if (this.httpServerThreadPool != null && !this.httpServerThreadPool.isShutdown()) {
            this.httpServerThreadPool.shutdownNow();
            this.logger.log(FINE, "http threadpool shutdown");
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
        this.logger.log(FINE, "Finished shutdown");
    }


    private record CachedResponse(int statusCode, byte[] body) {
    }


}
