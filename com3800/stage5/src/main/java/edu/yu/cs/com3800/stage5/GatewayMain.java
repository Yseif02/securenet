package edu.yu.cs.com3800.stage5;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class GatewayMain {

    /*
     * args:
     * 0 = httpPort
     * 1 = gatewayUdpPort
     * 2 = peerEpoch
     * 3 = gatewayId
     * 4 = numObservers
     * 5 = numPeers
     * 6 = portScaler
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println(
                    "Usage: GatewayMain <httpPort> <gatewayUdpPort> <peerEpoch> <gatewayId> <numObservers> <numPeers> <portScaler>"
            );
            System.exit(1);
        }

        int httpPort = Integer.parseInt(args[0]);
        int gatewayUdpPort = Integer.parseInt(args[1]);
        long peerEpoch = Long.parseLong(args[2]);
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
                new InetSocketAddress("localhost", gatewayUdpPort)
        );

        GatewayServer gateway =
                new GatewayServer(httpPort, gatewayUdpPort, peerEpoch, gatewayId, peerIDtoAddress, numObservers);

        gateway.start();
        gateway.join();
    }
}