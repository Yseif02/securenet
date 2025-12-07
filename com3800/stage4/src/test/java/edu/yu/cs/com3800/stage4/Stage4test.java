package edu.yu.cs.com3800.stage4;

import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.UDPMessageReceiver;
import edu.yu.cs.com3800.UDPMessageSender;
import edu.yu.cs.com3800.Vote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Stage4test {

    //public Stage4test() throws Exception {}

    private int[] ports = {8010, 8020, 8030, 8040};
    private ArrayList<PeerServerImpl> servers;
    private Client client;
    private GatewayServer gatewayServer;
    private HashMap<Integer, Long> portToId;


    @BeforeEach
    void setUp() throws IOException, URISyntaxException {

    }

    @AfterEach
    void tearDown() {
        for (PeerServerImpl server : this.servers) {
            server.shutdown();
            server = null;
        }

        this.gatewayServer.shutdown();
        this.gatewayServer = null;
    }

    private void localSetup(int numPeers, boolean differentEpoch, int portScaler) throws Exception {
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>(8);
        this.portToId = new HashMap<>();
        for (int i = 1; i <= numPeers; i++) {
            peerIDtoAddress.put(Integer.valueOf(i).longValue(), new InetSocketAddress("localhost", 8000 + (i * portScaler)));
            this.portToId.put(8000 + (i * portScaler), Integer.valueOf(i).longValue());
        }
        this.client = new Client("localhost", 8888);
        this.servers = new ArrayList<>(3);

        //observer
        int gatewayPort = 7999;
        Long gatewayId = 10000L;
        peerIDtoAddress.put(gatewayId, new InetSocketAddress("localhost", gatewayPort));
        this.portToId.put(gatewayPort, gatewayId);
        this.gatewayServer = new GatewayServer(8888, gatewayPort,0, gatewayId, new ConcurrentHashMap<>(peerIDtoAddress), 1);
        this.servers.add(this.gatewayServer.getGatewayPeerServer());
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (entry.getKey().equals(gatewayId)) {
                continue;
            }
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());
            Long halfway = (long) (numPeers / 2);
            PeerServerImpl server = (entry.getKey().equals(halfway) && differentEpoch) ?
                    //higher epoch, will win election
                    new PeerServerImpl(entry.getValue().getPort(), 1, entry.getKey(), map, gatewayId, 1) :
                    new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, gatewayId, 1) ;
            this.servers.add(server);
            //server.start();
        }
        //this.gatewayServer.getGatewayPeerServer().start();
        for (PeerServerImpl server : this.servers) {
            server.start();
        }
        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException ignored) {
        }
    }

    @Test
    void electCorrectLeader() throws Exception {
        int numPeers = 5;
        localSetup(numPeers, false, 10);
        for (PeerServer server : this.servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                Long serverLeaderFromPort = this.portToId.get(8000 + numPeers * 10);
                assertEquals(serverLeaderFromPort, leader.getProposedLeaderID());
            }
        }
    }

    @Test
    void electCorrectLeaderButLeaderIsNotLastId() throws Exception {
        int numPeers = 4;
        int expectedLeader = 2;
        localSetup(numPeers, true, 10);
        for (PeerServer server : this.servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                Long serverLeaderFromPort = this.portToId.get(8000 + expectedLeader * 10);
                assertEquals(serverLeaderFromPort, leader.getProposedLeaderID());
            }
        }
    }

    @Test
    void sendWorkAndReceiveGoodResponse() throws Exception {
        localSetup(3, false, 10);
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        int req = this.client.sendCompileAndRunRequest(validClass);
        HttpResponse<String> response = client.getResponse(req).get();
        assertEquals(200, response.statusCode());
        assertEquals("Hello world!", response.body());

    }

    @Test
    void testCaching() throws Exception {
        localSetup(3, false, 10);
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        int req1 = this.client.sendCompileAndRunRequest(validClass);
        HttpResponse<String> response1 = client.getResponse(req1).get();
        assertEquals(200, response1.statusCode());
        assertFalse(booleanValueOf(response1.headers().firstValue("Cached-Response").get()));

        Thread.sleep(500);
        int req2 = this.client.sendCompileAndRunRequest(validClass);
        HttpResponse<String> response2 = client.getResponse(req2).get();
        assertEquals("Hello world!", response1.body());
        assertEquals(200, response2.statusCode());
        assertEquals("Hello world!", response2.body());
        assertTrue(booleanValueOf(response2.headers().firstValue("Cached-Response").get()));
    }

    @Test
    void testLotsOfWorkFewNodes() throws Exception {
        localSetup(10, false, 5);
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";

        Client client = new Client("localhost", 8888);

        List<Integer> reqs = new ArrayList<>();
        //int lastReq = client.getLastReqId();
        for (int i = 1; i <= 100; i++) {
            String code = validClass.replace("world!", "world! from code version " + i);
            int reqId = client.sendCompileAndRunRequest(code);

            reqs.add(reqId);
            Thread.sleep(3);
        }
        for (Integer reqId : reqs) {
            HttpResponse<String> response = client.getResponse(reqId).get();
            String expected = "Hello world! from code version " + reqId;
            assertEquals(expected, response.body());
            System.out.println(response.body());
        }
    }

    @Test
    void allThreadsShutdown() throws Exception {
        localSetup(3, false, 10);

        for (PeerServerImpl server : this.servers) {
            System.out.println(server.getServerId());
            PeerServer.ServerState state = server.getPeerState();
            server.shutdown();
            Thread.sleep(5_000);
            UDPMessageReceiver receiver = (UDPMessageReceiver) getPrivateField((PeerServerImpl) server,"receiverWorker");
            assertFalse(receiver.isAlive());
            UDPMessageSender sender = (UDPMessageSender) getPrivateField((PeerServerImpl) server, "senderWorker");
            assertFalse(sender.isAlive());
            if (!(server instanceof GatewayPeerServerImpl)) {
                switch (state) {
                    case FOLLOWING -> {
                        JavaRunnerFollower followerThread = (JavaRunnerFollower) getPrivateField((PeerServerImpl) server, "javaRunnerFollower");
                        assertFalse(followerThread.isAlive());
                    }
                    case LEADING -> {
                        RoundRobinLeader roundRobinLeader = (RoundRobinLeader) getPrivateField((PeerServerImpl) server, "roundRobinLeader");
                        assertFalse(roundRobinLeader.isAlive());
                    }
                }
            }
        }

        assertFalse(this.gatewayServer.getGatewayPeerServer().isAlive());
        this.gatewayServer.shutdown();

        //HttpServer httpServer = (HttpServer) getPrivateField((GatewayServer) this.gatewayServer, "httpServer");
        //assertFalse(httpServer.);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:8888/compileandrun"))
                .POST(HttpRequest.BodyPublishers.ofString("test"))
                .build();

        assertThrows(Exception.class, () -> {
            client.send(request, HttpResponse.BodyHandlers.ofString());
        });
        ExecutorService threadPool = (ExecutorService) getPrivateField((GatewayServer) this.gatewayServer, "threadPool");
        assertTrue(threadPool.isShutdown() || threadPool.isTerminated());
        GatewayPeerServerImpl gatewayPeerServer = (GatewayPeerServerImpl) getPrivateField((GatewayServer) this.gatewayServer, "gatewayPeerServer");
        assertFalse(gatewayPeerServer.isAlive());

    }

    private Object getPrivateField(Object obj, String fieldName) {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                var field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException(new NoSuchFieldException());
    }

    @Test
    void testShutdownSequence1() throws Exception {
        localSetup(3, false, 10);
        for (PeerServerImpl server : this.servers) {
            //if (server instanceof GatewayPeerServerImpl) continue;
            server.shutdown();
        }

        //GatewayPeerServerImpl gatewayPeerServer = gatewayServer.getGatewayPeerServer();
        gatewayServer.shutdown();
        //gatewayPeerServer.shutdown();
    }

    @Test
    void testShutdownSequence2() throws Exception {
        localSetup(3, false, 10);
        for (PeerServerImpl server : this.servers) {
            if (server instanceof GatewayPeerServerImpl) continue;
            server.shutdown();
        }

        GatewayPeerServerImpl gatewayPeerServer = gatewayServer.getGatewayPeerServer();
        gatewayServer.shutdown();
        gatewayPeerServer.shutdown();
    }

    @Test
    void testShutdownSequence3() throws Exception {
        localSetup(3, false, 10);
        for (PeerServerImpl server : this.servers) {
            if (server instanceof GatewayPeerServerImpl) continue;
            server.shutdown();
        }

        GatewayPeerServerImpl gatewayPeerServer = gatewayServer.getGatewayPeerServer();
        gatewayPeerServer.shutdown();
        gatewayServer.shutdown();
    }

    @Test
    void testShutdownSequence4() throws Exception {
        localSetup(3, false, 10);
        gatewayServer.shutdown();
        for (PeerServerImpl server : this.servers) {
            //if (server instanceof GatewayPeerServerImpl) continue;
            server.shutdown();
        }


    }

    private boolean booleanValueOf(String s) {
        return s.equals("true");
    }


    private static class Client {
        private final HttpClient client;
        private final URI uri;
        private HashMap<Integer, CompletableFuture<HttpResponse<String>>> clientResponses;
        private final AtomicInteger clientRequestIdCounter = new AtomicInteger();
        private int lastReqId;

        private Client(String hostName, int hostPort) throws URISyntaxException {
            this.client = HttpClient.newHttpClient();
            this.uri = new URI("http", null, hostName, hostPort, "/compileandrun", null, null);
            this.clientResponses = new HashMap<>();
            this.lastReqId = 0;
        }

        private int sendCompileAndRunRequest(String src) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(this.uri)
                    .header("Content-Type", "text/x-java-source")
                    .POST(HttpRequest.BodyPublishers.ofString(src))
                    .build();

            int reqId = this.clientRequestIdCounter.incrementAndGet();
            this.clientResponses.put(reqId, this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete(((stringHttpResponse, throwable) ->{
                        if (throwable != null) {
                            System.err.println("Request " + reqId + " failed: " + throwable.getMessage());
                            this.clientResponses.put(reqId, CompletableFuture.failedFuture(throwable));
                        }
                    }
                    ))
            );
            this.lastReqId = reqId;
            return reqId;
        }

        private CompletableFuture<HttpResponse<String>> getResponse(int reqId) {
            return this.clientResponses.get(reqId);
        }

        private int getLastReqId() {
            return this.lastReqId;
        }
    }
}