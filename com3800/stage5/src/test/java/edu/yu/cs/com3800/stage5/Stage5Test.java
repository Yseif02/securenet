package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.Vote;
import org.junit.jupiter.api.AfterEach;

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
import java.util.concurrent.ExecutionException;
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
    void killServer() throws Exception {
        localSetup(7, false, 10);
        PeerServerImpl peerServer = this.servers.get(1);
        peerServer.interrupt();
        Thread.sleep(40000);
    }

    @Test
    void killLeader() throws Exception {
        localSetup(7, false, 10);
        PeerServerImpl peerServer = this.servers.getLast();
        peerServer.interrupt();
        Thread.sleep(40_000);
        for (PeerServer server : this.servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null && !this.gatewayServer.getPeerServer().isPeerDead(server.getServerId())) {
                System.out.println(server.getServerId() + ": \tLeader: " + leader.getProposedLeaderID());
                Long serverLeaderFromPort = this.portToId.get(8000 + 6 * 10);
                assertEquals(serverLeaderFromPort, leader.getProposedLeaderID());
            }
        }
        //Thread.sleep(40000);
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

    @Test
    void testWorkSentToFollowerThenFollowerKilled() throws Exception {
        localSetup(3, false, 10);

        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        String validClass2 = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world2!\";\n    }\n}\n";

        int req = this.client.sendCompileAndRunRequest(validClass);
        int req2 = this.client.sendCompileAndRunRequest(validClass2);
        Thread.sleep(5);
        PeerServerImpl peerServer = this.servers.get(1);
        peerServer.interrupt();
        HttpResponse<String> response = client.getResponse(req).get();
        HttpResponse<String> response2 = client.getResponse(req2).get();
        assertEquals(200, response.statusCode());
        assertEquals(200, response2.statusCode());
        assertEquals("Hello world!", response.body());
        assertEquals("Hello world2!", response2.body());
    }

    @Test
    void testKillFollowerThenWorkSentToDeadFollower() throws Exception {
        localSetup(3, false, 10);

        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        String validClass2 = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world2!\";\n    }\n}\n";

        PeerServerImpl peerServer = this.servers.get(1);
        peerServer.interrupt();
        Thread.sleep(500);
        int req = this.client.sendCompileAndRunRequest(validClass);
        int req2 = this.client.sendCompileAndRunRequest(validClass2);
        HttpResponse<String> response = client.getResponse(req).get();
        HttpResponse<String> response2 = client.getResponse(req2).get();
        assertEquals(200, response.statusCode());
        assertEquals(200, response2.statusCode());
        assertEquals("Hello world!", response.body());
        assertEquals("Hello world2!", response2.body());
    }



    @Test
    void sendInvalidWorkAndReceiveBadResponse() throws Exception {
        localSetup(3, false, 10);
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String Run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        int req = this.client.sendCompileAndRunRequest(validClass);
        HttpResponse<String> response = client.getResponse(req).get();
        assertEquals(400, response.statusCode());
        System.out.println(response.body());
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
        System.out.println(response2.body());
        System.out.println(response3.body());
        //assertEquals("Hello World2", response2.body());
        //assertEquals("Hello World3", response3.body());
    }

    @Test
    void testElectCorrectLeaderWithMultipleObservers() throws URISyntaxException, IOException, InterruptedException, ExecutionException {
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>(8);
        this.portToId = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            peerIDtoAddress.put(Integer.valueOf(i).longValue(), new InetSocketAddress("localhost", 8000 + (i * 10)));
            this.portToId.put(8000 + (i * 10), Integer.valueOf(i).longValue());
        }
        this.client = new Client("localhost", 8888);
        this.servers = new ArrayList<>(3);

        //observer
        int gatewayPort = 7999;
        Long gatewayId = 10000L;
        peerIDtoAddress.put(gatewayId, new InetSocketAddress("localhost", gatewayPort));
        this.portToId.put(gatewayPort, gatewayId);
        this.gatewayServer = new GatewayServer(8888, gatewayPort,0, gatewayId, new ConcurrentHashMap<>(peerIDtoAddress), 2);
        this.gatewayServer.start();
        this.servers.add(this.gatewayServer.getPeerServer());
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (entry.getKey().equals(gatewayId)) {
                continue;
            }
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());

            PeerServerImpl server =
                    new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, gatewayId, 2) ;
            this.servers.add(server);
            //server.start();
        }
        //this.gatewayServer.getGatewayPeerServer().start();
        boolean first = false;
        for (PeerServerImpl server : this.servers) {
            if (!first && !(server instanceof GatewayPeerServerImpl)) {
                server.setPeerState(PeerServer.ServerState.OBSERVER);
                first = true;
            }

            if (!(server instanceof GatewayPeerServerImpl)) {
                server.start();
            }
        }

        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException ignored) {
        }


        for (PeerServer server : this.servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                System.out.println(server.getServerId() + " state: " + server.getPeerState());
                Long serverLeaderFromPort = this.portToId.get(8000 + 4 * 10);
                assertEquals(serverLeaderFromPort, leader.getProposedLeaderID());
            }
        }

        Client client = new Client("localhost", 8888);

        List<Integer> reqs = new ArrayList<>();
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        for (int i = 1; i <= 15; i++) {
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

    @Test
    void testFirstLeaderDoesWorkSecondLeaderDoesWork() throws Exception {
        localSetup(5, false, 10);

        List<Integer> reqs = new ArrayList<>();
        String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";
        for (int i = 1; i <= 10; i++) {
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

        PeerServerImpl leader =  this.servers.getLast();
        leader.interrupt();
        servers.remove(leader);
        Thread.sleep(4000);

        for (int i = 11; i <= 20; i++) {
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


}