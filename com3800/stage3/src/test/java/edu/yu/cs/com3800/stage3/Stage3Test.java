package edu.yu.cs.com3800.stage3;

import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.UDPMessageReceiver;
import edu.yu.cs.com3800.UDPMessageSender;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;



class Stage3Test {
    private Map<Long, InetSocketAddress> peerIdToAddress;
    private final List<PeerServer> servers = new ArrayList<>();

    private int clientPort;
    private InetSocketAddress clientAddress;
    private LinkedBlockingQueue<Message> clientOutgoing;
    private LinkedBlockingQueue<Message> clientIncoming;
    private UDPMessageSender clientSender;
    private UDPMessageReceiver clientReceiver;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        this.peerIdToAddress = new HashMap<>();
        this.peerIdToAddress.put(1L, new InetSocketAddress("localhost", 8010));
        this.peerIdToAddress.put(2L, new InetSocketAddress("localhost", 8020));
        this.peerIdToAddress.put(3L, new InetSocketAddress("localhost", 8030));
        this.peerIdToAddress.put(4L, new InetSocketAddress("localhost", 8040));
        this.peerIdToAddress.put(5L, new InetSocketAddress("localhost", 8050));
        this.peerIdToAddress.put(6L, new InetSocketAddress("localhost", 8060));
        this.peerIdToAddress.put(7L, new InetSocketAddress("localhost", 8070));
        this.peerIdToAddress.put(8L, new InetSocketAddress("localhost", 8080));
        startServers(List.of(1L,2L,3L,4L,5L,6L,7L,8L));

        this.clientPort = getRandomOpenPort();
        this.clientAddress = new InetSocketAddress("localhost", this.clientPort);
        this.clientOutgoing = new LinkedBlockingQueue<>();
        this.clientIncoming = new LinkedBlockingQueue<>();
        this.clientSender = new UDPMessageSender(clientOutgoing, clientPort);
        this.clientReceiver = new UDPMessageReceiver(clientIncoming, clientAddress, clientPort,null);
        this.clientSender.setDaemon(true);
        this.clientReceiver.setDaemon(true);
        this.clientSender.start();
        this.clientReceiver.start();

        Thread.sleep(5000);
    }

    private int getRandomOpenPort() throws IOException {
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        shutdownServers();

        if (this.clientReceiver != null) {
            this.clientReceiver.shutdown();
            this.clientReceiver.join(500);
        }

        if (this.clientSender != null) {
            this.clientSender.shutdown();
            this.clientSender.join(500);
        }

        Thread.sleep(500);
    }

    private void startServers(List<Long> ids) {
        for (long id : ids) {
            startServer(id);
        }
    }

    private PeerServerImpl startServer(long id) {
        HashMap<Long, InetSocketAddress> map = new HashMap<>(this.peerIdToAddress);
        map.remove(id);
        InetSocketAddress address = this.peerIdToAddress.get(id);
        if (address == null) {
            throw new IllegalArgumentException("No address for server id " + id);
        }
        PeerServerImpl server = new PeerServerImpl(address.getPort(), 0, id, map);
        this.servers.add(server);
        new Thread(server, "Server-" + id + "-port-" + address.getPort()).start();
        return server;
    }

    private void shutdownServers() throws InterruptedException {
        for (PeerServer server : new ArrayList<>(this.servers)) {
            server.shutdown();
        }
        for (PeerServer server : this.servers) {
            try {
                ((Thread) server).join(1000);
            } catch (InterruptedException e) {}
        }

        this.servers.clear();
        Thread.sleep(200);
    }

    @Test
    void testLeaderAndFollowerThreadsStartCorrectly() {
        PeerServerImpl leader = getLeader();
        assertNotNull(leader);
        int leaders = 0;
        for (PeerServer server : this.servers) {
            if (server.getPeerState().equals(PeerServer.ServerState.LEADING)) {leaders++;}
            else {
                assertEquals(PeerServer.ServerState.FOLLOWING, server.getPeerState());
            }
        }
        assertEquals(1, leaders);
    }

    private PeerServerImpl getLeader() {
        for(PeerServer server : this.servers) {
            if (server.getPeerState().equals(PeerServer.ServerState.LEADING)) {
                return (PeerServerImpl) server;
            }
        }
        return null;
    }

    @Test
    void correctThreadOnlyOnLeaderAndFollower() {
        PeerServerImpl leader = getLeader();
        assertNotNull(leader);
        RoundRobinLeader leaderThread = (RoundRobinLeader) getPrivateField(leader, "roundRobinLeader");
        assertNotNull(leaderThread);
        assertTrue(leaderThread.isAlive());
        JavaRunnerFollower javaRunnerFollowerForLeader = (JavaRunnerFollower) getPrivateField(leader, "javaRunnerFollower");
        assertNull(javaRunnerFollowerForLeader);
        int leaders = 0;
        int followers = 0;
        for (PeerServer server : this.servers) {
            if (server.getPeerState().equals(PeerServer.ServerState.LEADING)) {
                leaders++;
                continue;
            }

            followers++;
            RoundRobinLeader roundRobinLeader = (RoundRobinLeader) getPrivateField((PeerServerImpl) server, "roundRobinLeader");
            assertNull(roundRobinLeader);

            JavaRunnerFollower followerThread = (JavaRunnerFollower) getPrivateField((PeerServerImpl) server, "javaRunnerFollower");
            assertNotNull(followerThread);
            assertTrue(followerThread.isAlive());
        }
        System.out.println(leaders);
        System.out.println(followers);
        System.out.println(leader.getCurrentLeader());

    }

    private Object getPrivateField(Object obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testWorkProcessedByFollower() throws InterruptedException {
        String code = "public class A { public String run() { return \"hi\";} }";
        sendWork(code);
        Message response = waitForResponse(5000);
        assertNotNull(response);
        assertEquals(Message.MessageType.COMPLETED_WORK, response.getMessageType());
        String respMessage = new String(response.getMessageContents()).trim();
        System.out.println(respMessage);
        assertEquals("hi", respMessage);
    }

    private void sendWork(String code) throws InterruptedException {
        PeerServerImpl leader = getLeader();
        assertNotNull(leader);
        int leaderPort = leader.getUdpPort();
        Message work = new Message(
                Message.MessageType.WORK,
                code.getBytes(StandardCharsets.UTF_8),
                this.clientAddress.getHostString(),
                this.clientPort,
                leader.getAddress().getHostString(),
                leaderPort
        );
        this.clientOutgoing.put(work);
    }

    private Message waitForResponse(long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            Message response = this.clientIncoming.poll();
            if (response != null) return response;
            Thread.sleep(20);
        }
        fail("Timed out waiting for response");
        return null;
    }

    /*@Test
    void testRoundRobinDistribution() throws InterruptedException {
        List<String> codes = List.of(
                "public class A { public String run() { return \"hi\";} }", //server1
                "public class B { public String run() { return \"hi\";} }", //server2
                "public class C { public String run() { return \"hi\";} }", //server3
                "public class E { public String run() { return \"hi\";} }", //server4
                "public class F { public String run() { return \"hi\";} }", //server5
                "public class H { public String run() { return \"hi\";} }", //server6
                "public class I { public String run() { return \"hi\";} }", //server7
                "public class J { public String run() { return \"hi\";} }"  //server1
        );
        for (String code : codes) sendWork(code);

        List<Message> responses = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            responses.add(waitForResponse(6000));
        }
        List<Integer> ports = responses.stream().map(Message::getSenderPort).toList();
        List<Integer> expectedPorts = List.of(8010, 8020, 8030, 8040, 8050, 8060, 8070, 8010);
        assertEquals(expectedPorts, ports);
    }*/

    @Test
    void testMultipleRequests() throws Exception {
        int count = 25;
        for (int i = 0; i < count; i++) {
            String code = "public class A { public String run(){ return \"X\";} }";
            sendWork(code);
        }
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(Objects.requireNonNull(waitForResponse(7000)).getRequestID());
        }
        assertEquals(count, ids.size());
    }

    @Test
    void testCompilationErrorFollowerToLeaderToClient() throws InterruptedException {
        String badCode = "public class A { public String run(){ return (5 } }";
        sendWork(badCode);
        String body = new String(Objects.requireNonNull(waitForResponse(5000)).getMessageContents());
        System.out.println(body);
        assertTrue(body.contains("compile"));
        assertTrue(body.contains("Error"));
        assertTrue(body.contains("Exception"));

    }

    @Test
    void testRuntimeErrorFollowerToLeaderToClient() throws InterruptedException {
        String badCode = "public class A { public String run(){ int x=5/0; return \"n\";} }";
        sendWork(badCode);
        String body = new String(Objects.requireNonNull(waitForResponse(5000)).getMessageContents());
        assertTrue(body.contains("/ by zero"));
    }

    @Test
    void testMissingCorrectRunMethodFollowerToLeaderToClient() throws InterruptedException {
        String badCode = "public class A { public String Run(){ int x=5/0; return \"n\";} }";
        sendWork(badCode);
        String body = new String(Objects.requireNonNull(waitForResponse(5000)).getMessageContents());
        System.out.println(body);
        assertTrue(body.contains("Could not create and run instance of class"));
        assertTrue(body.contains("ReflectiveOperationException"));
    }
}