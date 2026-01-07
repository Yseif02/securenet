package edu.yu.cs.com3800.stage5;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class PeerServerMain {

    /*
     * args:
     * 0 = udpPort
     * 1 = peerEpoch
     * 2 = serverId
     * 3 = gatewayId
     * 4 = numObservers
     * 5 = numPeers
     * 6 = portScaler
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println(
                    "Usage: PeerServerMain <udpPort> <peerEpoch> <serverId> <gatewayId> <numObservers> <numPeers> <portScaler>"
            );
            System.exit(1);
        }

        int udpPort = Integer.parseInt(args[0]);
        long peerEpoch = Long.parseLong(args[1]);
        long serverId = Long.parseLong(args[2]);
        long gatewayId = Long.parseLong(args[3]);
        int numObservers = Integer.parseInt(args[4]);
        int numPeers = Integer.parseInt(args[5]);
        int portScaler = Integer.parseInt(args[6]);

        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();

        for (int i = 1; i <= numPeers; i++) {
            peerIDtoAddress.put(
                    (long) i,
                    new InetSocketAddress("localhost", 8000 + (i * portScaler))
            );
        }

        peerIDtoAddress.put(
                gatewayId,
                new InetSocketAddress("localhost", 7999)
        );

        peerIDtoAddress.remove(serverId);

        PeerServerImpl server =
                new PeerServerImpl(udpPort, peerEpoch, serverId, peerIDtoAddress, gatewayId, numObservers);

        server.start();
        server.join(); // keep JVM alive
    }
}