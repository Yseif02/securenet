package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.UDPMessageReceiver;
import edu.yu.cs.com3800.UDPMessageSender;
import edu.yu.cs.com3800.Vote;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import edu.yu.cs.com3800.stage5.*;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class Stage5Test {


    private ArrayList<PeerServerImpl> servers;
    private Client client;
    private GatewayServer gatewayServer;
    private HashMap<Integer, Long> portToId;



    @AfterEach
    void tearDown() throws InterruptedException {
        for (PeerServerImpl server : this.servers) {
            server.shutdown();
            server = null;
        }

        this.gatewayServer.shutdown();
        this.gatewayServer.join();
        this.gatewayServer = null;
    }


    @Test
    void testSendWorkToGatewayQueueBeforeLeaderElected() throws Exception {
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>(8);
        this.portToId = new HashMap<>();
        for (int i = 1; i <= 3; i++) {
            peerIDtoAddress.put(Integer.valueOf(i).longValue(), new InetSocketAddress("localhost", 8000 + (i * 10)));
            this.portToId.put(8000 + (i * 10), Integer.valueOf(i).longValue());
        }
        this.client = new Client("localhost", 8888);
        this.servers = new ArrayList<>(3);
        int gatewayPort = 7999;
        Long gatewayId = 10000L;
        peerIDtoAddress.put(gatewayId, new InetSocketAddress("localhost", gatewayPort));
        this.portToId.put(gatewayPort, gatewayId);
        this.gatewayServer = new GatewayServer(8888, gatewayPort,0, gatewayId, new ConcurrentHashMap<>(peerIDtoAddress), 1);
        this.gatewayServer.start();
        this.servers.add(this.gatewayServer.getPeerServer());
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        int req = this.client.sendCompileAndRunRequest(validClass);
        Thread.sleep(50);
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (entry.getKey().equals(gatewayId)) {
                continue;
            }
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());

            PeerServerImpl server =
                    new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, gatewayId, 1);
            this.servers.add(server);
            //server.start();
        }
        for (PeerServerImpl server : this.servers) {
            if (!(server instanceof GatewayPeerServerImpl)) {
                server.start();
            }
        }
        Thread.sleep(5000);
        HttpResponse<String> response = client.getResponse(req).get();
        assertEquals(200, response.statusCode());
        assertEquals("Hello world!", response.body());
    }


    // tests whether the work gets reassigned to a different follower
    @Test
    void testWorkSentToFollowerThenFollowerKilled() throws Exception {
        localSetup(3, false, 10);

        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        String validClass2 = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world2!\";\n    }\n}\n";

        int req1 = this.client.sendCompileAndRunRequest(validClass);
        int req2 = this.client.sendCompileAndRunRequest(validClass2);
        Thread.sleep(5);
        PeerServerImpl peerServer1 = this.servers.get(1);
        PeerServerImpl peerServer2 = this.servers.get(2);
        peerServer1.interrupt();
        HttpResponse<String> response = client.getResponse(req1).get();
        HttpResponse<String> response2 = client.getResponse(req2).get();
        assertEquals(200, response.statusCode());
        assertEquals(200, response2.statusCode());
        assertEquals("Hello world!", response.body());
        assertEquals("Hello world2!", response2.body());

        PeerServerImpl leader = this.servers.getLast();

        RoundRobinLeader rrl =
                (RoundRobinLeader) getPrivateField(leader, "roundRobinLeader");

        ConcurrentHashMap<Long, CopyOnWriteArrayList<Long>> history =
                (ConcurrentHashMap<Long, CopyOnWriteArrayList<Long>>)
                        getPrivateField(rrl, "assignmentHistory");

        assertTrue(history.containsKey((long) req1));
        assertTrue(history.containsKey((long) req2));


        int size1 = new HashSet<>(history.get((long) req1)).size();
        System.out.println("size1 " + size1);
        int size2 =  new HashSet<>(history.get((long) req2)).size();
        System.out.println("size2 " + size2);

        assertTrue((size1 == 1 && size2 == 2) || (size1 == 2 && size2 == 1));

        JavaRunnerFollower jrf1 = (JavaRunnerFollower) getPrivateField(peerServer1, "javaRunnerFollower");
        JavaRunnerFollower jrf2 = (JavaRunnerFollower) getPrivateField(peerServer2, "javaRunnerFollower");


        /*long jrf1NumRequests = (long) getPrivateField(jrf1, "numRequests");
        long jrf2NumRequests = (long) getPrivateField(jrf2, "numRequests");
        System.out.println("jrfn1 " + jrf1NumRequests);
        System.out.println("jrfn2 " + jrf2NumRequests);
        assertTrue((jrf1NumRequests == 1 && jrf2NumRequests == 2) || (jrf1NumRequests == 2 && jrf2NumRequests == 1));*/
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
    void sendInvalidWorkAndReceiveBadResponse() throws Exception {
        localSetup(3, false, 10);
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String Run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        int req = this.client.sendCompileAndRunRequest(validClass);
        HttpResponse<String> response = client.getResponse(req).get();
        assertEquals(400, response.statusCode());
        //System.out.println(response.body());
        //assertEquals("Hello world!", response.body());

    }

    @Test
    void testSendWorkToLeaderKillLeaderGetWorkFromNextLeader() throws Exception {
        localSetup(4, false, 10);
        String validSleepClass1 =
                        "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World1\";\n" +
                        "    }\n" +
                        "}\n";

        String validSleepClass2 =
                        "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World2\";\n" +
                        "    }\n" +
                        "}\n";

        String validSleepClass3 =
                        "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World3\";\n" +
                        "    }\n" +
                        "}\n";

        int req1 = this.client.sendCompileAndRunRequest(validSleepClass1);
        int req2 = this.client.sendCompileAndRunRequest(validSleepClass2);
        int req3 = this.client.sendCompileAndRunRequest(validSleepClass3);
        Thread.sleep(1500);
        this.servers.getLast().interrupt();

        HttpResponse<String> response1 = client.getResponse(req1).get();
        HttpResponse<String> response2 = client.getResponse(req2).get();
        HttpResponse<String> response3 = client.getResponse(req3).get();
        assertEquals(200, response1.statusCode());
        assertEquals(200, response2.statusCode());
        assertEquals(200, response3.statusCode());
        assertEquals("Hello World1", response1.body());
        assertEquals("Hello World2", response2.body());
        assertEquals("Hello World3", response3.body());
    }

    @Test
    void testSendBadWorkToLeaderKillLeaderGetWorkFromNextLeader() throws Exception {
        localSetup(4, false, 10);
        String validSleepClass1 =
                        "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World1\";\n" +
                        "    }\n" +
                        "}\n";

        String invalidSleepClass2 =
                        "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "            throw new ReflectiveOperationException(); " +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World2\";\n" +
                        "    }\n" +
                        "}\n";

        String invalidSleepClass3 =
                        "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "            int err = 4 / 0;\n" +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World3\";\n" +
                        "    }\n" +
                        "}\n";

        int req1 = this.client.sendCompileAndRunRequest(validSleepClass1);
        int req2 = this.client.sendCompileAndRunRequest(invalidSleepClass2);
        int req3 = this.client.sendCompileAndRunRequest(invalidSleepClass3);
        Thread.sleep(1500);
        this.servers.getLast().interrupt();

        HttpResponse<String> response1 = client.getResponse(req1).get();
        HttpResponse<String> response2 = client.getResponse(req2).get();
        HttpResponse<String> response3 = client.getResponse(req3).get();
        assertEquals(200, response1.statusCode());
        assertEquals(400, response2.statusCode());
        assertEquals(400, response3.statusCode());
        assertEquals("Hello World1", response1.body());

        //assertEquals("Hello World2", response2.body());
        //assertEquals("Hello World3", response3.body());
    }





    @Test
    void testLateFollowerGetsAddedToFollowerListAndGetsWork() throws Exception {
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>(8);
        this.portToId = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            peerIDtoAddress.put(Integer.valueOf(i).longValue(), new InetSocketAddress("localhost", 8000 + (i * 10)));
            this.portToId.put(8000 + (i * 10), Integer.valueOf(i).longValue());
        }
        this.client = new Client("localhost", 8888);
        this.servers = new ArrayList<>(3);
        int gatewayPort = 7999;
        Long gatewayId = 10000L;
        peerIDtoAddress.put(gatewayId, new InetSocketAddress("localhost", gatewayPort));
        this.portToId.put(gatewayPort, gatewayId);
        this.gatewayServer = new GatewayServer(8888, gatewayPort,0, gatewayId, new ConcurrentHashMap<>(peerIDtoAddress), 1);
        this.gatewayServer.start();
        this.servers.add(this.gatewayServer.getPeerServer());
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (entry.getKey().equals(gatewayId)) {
                continue;
            }
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());

            PeerServerImpl server =
                    new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, gatewayId, 1) ;
            this.servers.add(server);

        }
        PeerServerImpl lateFollower = null;
        boolean first = true;
        for (PeerServerImpl server : this.servers) {
            if (!(server instanceof GatewayPeerServerImpl)) {
                if (first) {
                    lateFollower = server;
                    first = false;
                    continue;
                }
                server.start();
            }
        }
        Thread.sleep(5000);
        lateFollower.start();
        Thread.sleep(5000);

        List<Integer> reqs = new ArrayList<>();
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        for (int i = 1; i <= 5; i++) {
            String code = validClass.replace("world!", "world! from code version " + i);
            int reqId = client.sendCompileAndRunRequest(code);

            reqs.add(reqId);
            Thread.sleep(1);
        }

        for (Integer reqId : reqs) {
            HttpResponse<String> response = client.getResponse(reqId).get();
            String expected = "Hello world! from code version " + reqId;
            assertEquals(expected, response.body());
            System.out.println(response.body());
        }

    }


    String makeClass(int reqId, String msg) {
        return
                "package edu.yu.cs.fall2019.com3800.stage1;\n\n" +
                        "public class HelloWorld_" + reqId + " {\n" +
                        "    public String run() {\n" +
                        "        return \"" + msg + "\";\n" +
                        "    }\n" +
                        "}\n";
    }

    @Test
    void reassignWorkFromDeadFollowerToAliveFollower() throws Exception {
        localSetup(3, false, 10);
        String validSleepClass1 =
                "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World1\";\n" +
                        "    }\n" +
                        "}\n";

        String validSleepClass2 =
                "public class ValidSleepClass {\n" +
                        "    public String run() {\n" +
                        "        try {\n" +
                        "            Thread.sleep(10000);\n" +
                        "        } catch (InterruptedException e) {\n" +
                        "            return \"interrupted\";\n" +
                        "        }\n" +
                        "        return \"Hello World2\";\n" +
                        "    }\n" +
                        "}\n";

        int req1 = this.client.sendCompileAndRunRequest(validSleepClass1);
        int req2 = this.client.sendCompileAndRunRequest(validSleepClass2);
        Thread.sleep(2000);
        this.servers.get(1).interrupt();
        HttpResponse<String> response1 = client.getResponse(req1).get();
        HttpResponse<String> response2 = client.getResponse(req2).get();
        assertEquals(200, response1.statusCode());
        assertEquals(200, response2.statusCode());
        assertEquals("Hello World1", response1.body());
        assertEquals("Hello World2", response2.body());
    }

    @Test
    void allThreadsShutdown() throws Exception {
        localSetup(3, false, 10);

        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        int req = this.client.sendCompileAndRunRequest(validClass);

        for (PeerServerImpl server : this.servers) {
            //System.out.println(server.getServerId());
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
                        Thread finishThread =
                                (Thread) getPrivateField(roundRobinLeader, "finishRequestThread");

                        assertFalse(finishThread.isAlive());
                        TCPServer tcpServer =
                                (TCPServer) getPrivateField(roundRobinLeader, "tcpServer");

                        tcpServer.shutdown();
                        tcpServer.join(5_000);
                        assertFalse(tcpServer.isAlive());
                        ExecutorService tcpThreadPool =
                                (ExecutorService) getPrivateField(tcpServer, "threadPool");

                        assertTrue(tcpThreadPool.isShutdown() || tcpThreadPool.isTerminated());

                        ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses =
                                (ConcurrentHashMap<Long, CompletableFuture<byte[]>>) getPrivateField(tcpServer, "pendingResponses");

                        assertTrue(pendingResponses.isEmpty());
                    }
                }
            }
        }

        assertFalse(this.gatewayServer.getPeerServer().isAlive());
        this.gatewayServer.shutdown();
        this.gatewayServer.join();
        assertFalse(this.gatewayServer.isAlive());
        LinkedBlockingQueue<byte[]> queuedRequests =
                (LinkedBlockingQueue<byte[]>) getPrivateField(this.gatewayServer, "queuedRequests");
        assertTrue(queuedRequests.isEmpty());

        ConcurrentHashMap<Long, CompletableFuture<byte[]>> clientRequests =
                (ConcurrentHashMap<Long, CompletableFuture<byte[]>>) getPrivateField(gatewayServer, "clientRequests");

        assertTrue(clientRequests.isEmpty());

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
        ExecutorService threadPool1 = (ExecutorService) getPrivateField((GatewayServer) this.gatewayServer, "httpServerThreadPool");
        ExecutorService threadPool2 = (ExecutorService) getPrivateField((GatewayServer) this.gatewayServer, "gatewayServerThreadPool");
        assertTrue(threadPool1.isShutdown() || threadPool1.isTerminated());
        assertTrue(threadPool2.isShutdown() || threadPool2.isTerminated());
        GatewayPeerServerImpl gatewayPeerServer = (GatewayPeerServerImpl) getPrivateField((GatewayServer) this.gatewayServer, "gatewayPeerServer");
        assertFalse(gatewayPeerServer.isAlive());
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
        this.gatewayServer.start();
        this.servers.add(this.gatewayServer.getPeerServer());
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
            if (!(server instanceof GatewayPeerServerImpl)) {
                server.start();
            }
        }
        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException ignored) {
        }
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

        private void clearClientResponses() {
            this.clientResponses.clear();
        }

        private void resetReqId() {
            this.clientRequestIdCounter.set(0);
            this.lastReqId = 0;
        }

        private int getLastReqId() {
            return this.lastReqId;
        }
    }

    @Test
    void testSummaryAndVerboseLogEndpoints() throws Exception {
        // Start cluster
        localSetup(3, false, 10);

        // Give election time (do NOT sleep arbitrarily)

        // Test gateway first
        int gatewayUdpPort = 7999;
        int gatewayHttpPort = gatewayUdpPort + 3;

        assertLogEndpointWorks(gatewayHttpPort, "/summary");
        assertLogEndpointWorks(gatewayHttpPort, "/verbose");

        // Test peers
        for (PeerServer server : this.servers) {
            int udpPort = server.getUdpPort();
            int httpPort = udpPort + 3;

            assertLogEndpointWorks(httpPort, "/summary");
            assertLogEndpointWorks(httpPort, "/verbose");
        }
    }

    private void assertLogEndpointWorks(int httpPort, String path) throws Exception {
        URI uri = new URI("http://localhost:" + httpPort + path);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<byte[]> response = pollUntil200(client, request, 5000);

        assertEquals(200, response.statusCode(), "Expected 200 from " + uri);
        assertNotNull(response.body(), "Response body is null for " + uri);
        assertTrue(response.body().length > 0, "Empty log body for " + uri);
    }

    private HttpResponse<byte[]> pollUntil200(HttpClient client, HttpRequest request, long timeoutMillis) throws Exception {

        long deadline = System.currentTimeMillis() + timeoutMillis;
        HttpResponse<byte[]> lastResponse = null;

        while (System.currentTimeMillis() < deadline) {
            lastResponse = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (lastResponse.statusCode() == 200) {
                return lastResponse;
            }

            Thread.sleep(100);
        }

        fail("Endpoint never returned 200: " + request.uri());
        return lastResponse;
    }

    @Test
    void testCaching() throws Exception {
        localSetup(3, false, 10);
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        List<Integer> reqs = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String code = validClass.replace("world!", "world! from code version " + i);
            int reqId = client.sendCompileAndRunRequest(code);
            reqs.add(reqId);
        }

        //int req1 = this.client.sendCompileAndRunRequest(validClass);
        for (Integer reqId : reqs) {
            HttpResponse<String> response1 = client.getResponse(reqId).get();
            assertEquals("Hello world! from code version " + reqId, response1.body());
            assertEquals(200, response1.statusCode());
            assertFalse(booleanValueOf(response1.headers().firstValue("Cached-Response").get()));
        }

        Thread.sleep(500);
        reqs.clear();
        this.client.clearClientResponses();
        this.client.resetReqId();
        for (int j = 1; j <= 20; j++) {
            String code = validClass.replace("world!", "world! from code version " + j);
            int reqId = client.sendCompileAndRunRequest(code);
            reqs.add(reqId);
        }

        for (Integer reqId : reqs) {
            HttpResponse<String> response1 = client.getResponse(reqId).get();
            assertEquals("Hello world! from code version " + reqId, response1.body());
            assertEquals(200, response1.statusCode());
            assertTrue(booleanValueOf(response1.headers().firstValue("Cached-Response").get()));
        }
    }
    private boolean booleanValueOf(String s) {
        return s.equals("true");
    }

    @Test
    void wrongHeader() throws Exception {
        localSetup(3, false, 10);
        HttpClient client = HttpClient.newHttpClient();
        String validClass3 = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld3\n{\n    public String run()\n    {\n        return \"Hello world! 3\";\n    }\n}\n";

        URI uri = new URI("http", null, "localhost", 8888, "/compileandrun", null, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "binary-data")
                .POST(HttpRequest.BodyPublishers.ofString(validClass3))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String expectedResponse = "Content-Type must be \"text/x-java-source\"";
        int expectedCode = 400;
        assertEquals(expectedResponse, response.body());
        assertEquals(expectedCode, response.statusCode());


    }

    @Test
    void wrongType() throws Exception{
        localSetup(3, false, 10);
        HttpClient client = HttpClient.newHttpClient();
        String validClass3 = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld3\n{\n    public String run()\n    {\n        return \"Hello world! 3\";\n    }\n}\n";

        URI uri = new URI("http", null, "localhost", 8888, "/compileandrun", null, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "text/x-java-source")
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String expectedResponse = "Method not allowed DELETE. Only POST allowed.";
        int expectedCode = 405;
        assertEquals(expectedResponse, response.body());
        assertEquals(expectedCode, response.statusCode());

    }

    @Test
    void testBadCode() throws Exception{
        localSetup(3, false, 10);
        String invalidClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String Run()\n    {\n        return \"Hello world2!\";\n    }\n}\n";

        int req = client.sendCompileAndRunRequest(invalidClass);
        CompletableFuture<HttpResponse<String>> completableFuture = client.clientResponses.get(req);
        HttpResponse<String> response = completableFuture.get();
        assertTrue(response.body().startsWith("Could not create and run instance of class"));
        assertEquals(400, response.statusCode());
    }


}