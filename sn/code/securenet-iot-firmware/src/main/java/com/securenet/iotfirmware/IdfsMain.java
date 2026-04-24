package com.securenet.iotfirmware;

import com.securenet.common.EpsLoadBalancer;
import com.securenet.common.LoadBalancer;
import com.securenet.iotfirmware.server.IdfsServer;
import com.securenet.storage.StorageGateway;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Entry point for the IoT Device Firmware Service.
 *
 * <p>All three IDFS instances are now identical — none of them embeds the
 * MQTT broker. The broker runs as a separate process ({@code MqttBrokerMain})
 * and all IDFS instances connect to it as clients, just like devices do.
 * This means all three instances are fully restartable by the ClusterManager.
 *
 * <p>Args:
 * <pre>
 *   --http-port           &lt;port&gt;           (default 8080)
 *   --host                &lt;host&gt;           (default 0.0.0.0)
 *   --mqtt-broker-url     &lt;url&gt;            (default tcp://localhost:1883)
 *   --dms-urls            &lt;comma-list&gt;
 *   --eps-urls            &lt;comma-list&gt;
 *   --vss-urls            &lt;comma-list&gt;
 *   --storage-url         &lt;comma-list&gt;
 *   --cluster-manager-url &lt;url&gt;
 *   --instance-index      &lt;int&gt;            (0-based slot, default 0)
 *   --idfs-cluster-size   &lt;int&gt;            (total slots, default 3)
 * </pre>
 */
public class IdfsMain {

    private static final Logger log = Logger.getLogger(IdfsMain.class.getName());

    public static void main(String[] args) throws Exception {
        int httpPort = 8080;
        String host  = "0.0.0.0";
        String dmsUrls           = "http://localhost:9002,http://localhost:9012,http://localhost:9022";
        String epsUrls           = "http://localhost:9003,http://localhost:9103,http://localhost:9203";
        String vssUrls           = "http://localhost:9005,http://localhost:9015,http://localhost:9025";
        String clusterManagerUrl = "http://localhost:9090";
        String storageUrl        = "http://localhost:9000,http://localhost:9010,http://localhost:9020";
        String mqttBrokerUrl     = "tcp://localhost:1883";
        int instanceIndex   = 0;   // which slot this process owns (0-based)
        int idfsClusterSize = 3;   // total number of IDFS slots

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--http-port"            -> httpPort         = Integer.parseInt(args[++i]);
                case "--host"                 -> host             = args[++i];
                case "--dms-urls"             -> dmsUrls         = args[++i];
                case "--eps-urls"             -> epsUrls         = args[++i];
                case "--vss-urls"             -> vssUrls         = args[++i];
                case "--cluster-manager-url"  -> clusterManagerUrl = args[++i];
                case "--mqtt-broker-url"      -> mqttBrokerUrl   = args[++i];
                case "--storage-url"          -> storageUrl      = args[++i];
                case "--instance-index"       -> instanceIndex   = Integer.parseInt(args[++i]);
                case "--idfs-cluster-size"    -> idfsClusterSize = Integer.parseInt(args[++i]);
            }
        }

        log.info("=== SecureNet IDFS ===");
        log.info("  Host:             " + host);
        log.info("  HTTP port:        " + httpPort);
        log.info("  MQTT broker URL:  " + mqttBrokerUrl);
        log.info("  DMS URLs:         " + dmsUrls);
        log.info("  EPS URLs:         " + epsUrls);
        log.info("  VSS URLs:         " + vssUrls);
        log.info("  Instance index:   " + instanceIndex + " / " + idfsClusterSize);

        LoadBalancer dmsLoadBalancer = new LoadBalancer("DMS", Arrays.asList(dmsUrls.split(",")));
        dmsLoadBalancer.watchClusterManager(clusterManagerUrl, "DMS");
        dmsLoadBalancer.start();

        LoadBalancer epsLoadBalancer = new EpsLoadBalancer(Arrays.asList(epsUrls.split(",")));
        epsLoadBalancer.watchClusterManager(clusterManagerUrl, "EPS");
        epsLoadBalancer.start();

        LoadBalancer vssLoadBalancer = new LoadBalancer("VSS", Arrays.asList(vssUrls.split(",")));
        vssLoadBalancer.watchClusterManager(clusterManagerUrl, "VSS");
        vssLoadBalancer.start();

        StorageGateway storageGateway = new StorageGateway(storageUrl, clusterManagerUrl);

        IdfsServer idfs = new IdfsServer(
                host, httpPort,
                dmsLoadBalancer, epsLoadBalancer, vssLoadBalancer,
                mqttBrokerUrl, storageGateway,
                instanceIndex, idfsClusterSize);  // <-- slot ownership args wired in
        idfs.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[IDFS] Shutdown signal received");
            idfs.stop();
        }));

        log.info("[IDFS] Ready — HTTP on " + host + ":" + httpPort
                + " MQTT broker: " + mqttBrokerUrl
                + " slot: " + instanceIndex + "/" + idfsClusterSize);
        Thread.currentThread().join();
    }
}
