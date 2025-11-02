package edu.yu.cs.com3800.stage2;

import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.Vote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Stage2Test {
    private HashMap<Long, InetSocketAddress> peerIdToAddress;
    private final List<PeerServer> servers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        this.peerIdToAddress = new HashMap<>(10);
        this.peerIdToAddress.put(1L, new InetSocketAddress("localhost", 8010));
        this.peerIdToAddress.put(2L, new InetSocketAddress("localhost", 8020));
        this.peerIdToAddress.put(3L, new InetSocketAddress("localhost", 8030));
        this.peerIdToAddress.put(4L, new InetSocketAddress("localhost", 8040));
        this.peerIdToAddress.put(5L, new InetSocketAddress("localhost", 8050));
        this.peerIdToAddress.put(6L, new InetSocketAddress("localhost", 8060));
        this.peerIdToAddress.put(7L, new InetSocketAddress("localhost", 8070));
        this.peerIdToAddress.put(8L, new InetSocketAddress("localhost", 8080));
        this.peerIdToAddress.put(9L, new InetSocketAddress("localhost", 8090));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        shutdownServers();
    }

    @Test
    void testElectHighestId() throws InterruptedException {
        startServers(List.of(1L,2L,3L,4L,5L,6L,7L,8L));

        long timeout = 10000L;
        long deadline = System.currentTimeMillis() + timeout;
        long agreed = -1;
        while (System.currentTimeMillis() < deadline) {
            boolean allHaveLeader = true;
            Long proposedLeaderId = null;
            for (PeerServer server : servers) {
                Vote vote = server.getCurrentLeader();
                if (vote == null) {
                    allHaveLeader = false;
                    break;
                }
                long id = vote.getProposedLeaderID();
                if (proposedLeaderId == null) {
                    proposedLeaderId = id;
                }
                else if (proposedLeaderId != id) {
                    allHaveLeader = false;
                    break;
                }
            }
            if (allHaveLeader && proposedLeaderId != null) {
                agreed = proposedLeaderId;
                break;
            }
            Thread.sleep(50);
        }
        assertNotEquals(-1, agreed);
        assertEquals(8L, agreed);
    }

    @Test
    void testLateJoinerHigherIdDuringCoronationSupersedes() throws InterruptedException {
        startServers(List.of(1L,2L,3L,4L,5L,6L,7L,8L));
        Thread.sleep(500);

        startServer(9L);

        long timeout = 12000L;
        long deadline = System.currentTimeMillis() + timeout;
        Long agreed = null;
        while (System.currentTimeMillis() < deadline) {
            boolean allHaveLeader = true;
            Long proposedLeaderId = null;
            for (PeerServer server : servers) {
                Vote vote = server.getCurrentLeader();
                if (vote == null) {
                    allHaveLeader = false;
                    break;
                }
                long id = vote.getProposedLeaderID();
                if (proposedLeaderId == null) {
                    proposedLeaderId = id;
                } else if (!proposedLeaderId.equals(id)) {
                    allHaveLeader = false;
                    break;
                }
            }
            if (allHaveLeader && proposedLeaderId != null) {
                agreed = proposedLeaderId;
                break;
            }
            Thread.sleep(50);
        }

        assertNotNull(agreed);
        assertEquals(9L, (long) agreed);
    }

    @Test
    void testLateJoinerAfterCoronationDoesNotSupersede() throws InterruptedException {
        startServers(List.of(1L,2L,3L,4L,5L,6L,7L,8L));


        long timeout = 12000L;
        long deadline = System.currentTimeMillis() + timeout;
        long agreed = -1;
        while (System.currentTimeMillis() < deadline) {
            boolean allHaveLeader = true;
            Long proposedLeaderId = null;
            for (PeerServer server : servers) {
                Vote vote = server.getCurrentLeader();
                if (vote == null) {
                    allHaveLeader = false;
                    break;
                }
                long id = vote.getProposedLeaderID();
                if (proposedLeaderId == null) {
                    proposedLeaderId = id;
                } else if (!proposedLeaderId.equals(id)) {
                    allHaveLeader = false;
                    break;
                }
            }
            if (allHaveLeader && proposedLeaderId != null) {
                agreed = proposedLeaderId;
                break;
            }
            Thread.sleep(50);
        }
        assertNotEquals(-1, agreed);
        assertEquals(8L, (long) agreed);

        Thread.sleep(4000);
        PeerServerImpl server = startServer(9L);
        Thread.sleep(500);
        Vote vote = server.getCurrentLeader();
        assertEquals(8L, vote.getProposedLeaderID());

        int leaders = 0;
        for (PeerServer peerServer : this.servers) {
            switch (peerServer.getPeerState()) {
                case LEADING:
                    assertEquals(8L, peerServer.getServerId());
                    leaders++;
                    break;
                case FOLLOWING:
                    assertNotEquals(8L, peerServer.getServerId());
                    break;
                case LOOKING:
                    fail();
                    break;
                default:
                    break;
            }
        }
        assertEquals(1, leaders);
    }

    @Test
    void testSingleNode() throws InterruptedException {
        long nodeToKeep = 5L;
        this.peerIdToAddress.keySet().removeIf(id -> id != nodeToKeep);
        startServer(5);

        long timeout = 5000L;
        long deadline = System.currentTimeMillis() + timeout;
        long agreed = -1;
        while (System.currentTimeMillis() < deadline) {
            boolean allHaveLeader = true;
            Long proposedLeaderId = null;
            for (PeerServer server : this.servers) {
                Vote vote = server.getCurrentLeader();
                if (vote == null) {
                    allHaveLeader = false;
                    break;
                }
                long id = vote.getProposedLeaderID();
                if (proposedLeaderId == null) {
                    proposedLeaderId = id;
                } else if (!proposedLeaderId.equals(id)) {
                    allHaveLeader = false;
                    break;
                }
            }
            if (allHaveLeader && proposedLeaderId != null) {
                agreed = proposedLeaderId;
                break;
            }
            Thread.sleep(50);
        }
        assertNotEquals(-1, agreed);
        assertEquals(5L, agreed);

        int leaders = 0;
        for (PeerServer peerServer : this.servers) {
            switch (peerServer.getPeerState()) {
                case LEADING:
                    assertEquals(5L, peerServer.getServerId());
                    leaders++;
                    break;
                case FOLLOWING:
                    assertNotEquals(5L, peerServer.getServerId());
                    break;
                case LOOKING:
                    fail("Should not be looking");
                    break;
            }
        }
        assertEquals(1, leaders);
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
        this.servers.clear();
        Thread.sleep(200);
    }


}
