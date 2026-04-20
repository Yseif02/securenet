package com.securenet.demo;

import com.securenet.demo.failover.FailoverResult;
import com.securenet.demo.failover.ServiceFailoverTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Failover Demo — verifies that load balancers discover restarted service
 * instances on new ports via ClusterManager polling.
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>Full platform running: {@code ./scripts/start-platform.sh}</li>
 *   <li>ClusterManager up at {@code localhost:9090}</li>
 *   <li>PID files present in {@code pids/} directory</li>
 *   <li>At least 30s since startup (ClusterManager initial delay)</li>
 * </ul>
 *
 * <h3>What it tests</h3>
 * <p>For each load-balanced service (Storage, UMS, DMS, IDFS, EPS,
 * Notification, VSS):
 * <ol>
 *   <li>Verifies all 3 instances healthy via LB</li>
 *   <li>Confirms round-robin distributes across all 3 ports</li>
 *   <li>Kills instance-2 via its PID file (SIGTERM)</li>
 *   <li>Waits for LB health check to drop it from rotation</li>
 *   <li>Confirms only 2 ports receive traffic</li>
 *   <li>Waits for ClusterManager to detect + restart on new port</li>
 *   <li>Confirms LB discovers new URL and routes traffic to it</li>
 * </ol>
 *
 * <p>EPS is tested differently: it restarts on the same port (Raft
 * constraint), so phase 6 asserts the original URL recovers rather than
 * a new URL appearing.
 *
 * <p>Storage is tested via its LoadBalancer inside StorageGateway — the
 * demo creates a standalone LoadBalancer pointed at the storage instances
 * to verify the same discovery behavior.
 *
 * <h3>Run</h3>
 * <pre>
 * java -cp "$(find . -name '*.jar' -path '*\/target\/*' | grep -v sources | tr '\n' ':')" \
 *   com.securenet.demo.FailoverDemo [--pids-dir ./pids] [--cluster-manager http://localhost:9090]
 * </pre>
 */
public class FailoverDemo {

    private static final String DEFAULT_CLUSTER_MANAGER = "http://localhost:9090";
    private static final String DEFAULT_PIDS_DIR        = "./pids";

    public static void main(String[] args) throws Exception {
        String clusterManagerUrl = DEFAULT_CLUSTER_MANAGER;
        String pidsDir           = DEFAULT_PIDS_DIR;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cluster-manager" -> clusterManagerUrl = args[++i];
                case "--pids-dir"        -> pidsDir           = args[++i];
            }
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          SecureNet — Load Balancer Failover Demo             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  ClusterManager: " + clusterManagerUrl);
        System.out.println("  PID directory:  " + pidsDir);
        System.out.println();

        // Pre-flight: ClusterManager must be reachable
        if (!checkClusterManager(clusterManagerUrl)) {
            System.out.println("❌ Cannot reach ClusterManager at " + clusterManagerUrl);
            System.out.println("   Make sure the platform is running: ./scripts/start-platform.sh");
            System.exit(1);
        }
        System.out.println("  ✓ ClusterManager reachable");

        // Pre-flight: PID files must exist
        if (!checkPidFiles(pidsDir)) {
            System.exit(1);
        }
        System.out.println("  ✓ PID files present");
        System.out.println();
        System.out.println("  NOTE: Each test kills a service instance and waits up to 90s");
        System.out.println("        for ClusterManager to detect + restart it. Total runtime");
        System.out.println("        can be 10-15 minutes for all 7 services.");
        System.out.println();

        List<FailoverResult> results = new ArrayList<>();

        // ── 1. Storage (tested via standalone LB, not StorageGateway) ────────
        results.add(new ServiceFailoverTest(
                "Storage",
                pidsDir + "/storage-2.pid",
                List.of("http://localhost:9000",
                        "http://localhost:9010",
                        "http://localhost:9020"),
                clusterManagerUrl,
                false
        ).run());

        pauseBetweenTests();

        // ── 2. UMS ────────────────────────────────────────────────────────────
        results.add(new ServiceFailoverTest(
                "UMS",
                pidsDir + "/ums-2.pid",
                List.of("http://localhost:9001",
                        "http://localhost:9011",
                        "http://localhost:9021"),
                clusterManagerUrl,
                false
        ).run());

        pauseBetweenTests();

        // ── 3. DMS ────────────────────────────────────────────────────────────
        results.add(new ServiceFailoverTest(
                "DMS",
                pidsDir + "/dms-2.pid",
                List.of("http://localhost:9002",
                        "http://localhost:9012",
                        "http://localhost:9022"),
                clusterManagerUrl,
                false
        ).run());

        pauseBetweenTests();

        // ── 4. IDFS ───────────────────────────────────────────────────────────
        // Note: idfs-1 hosts the MQTT broker and has no restart script.
        // We test idfs-2 (port 8081). idfs-1 is instance 0 in the URL list
        // but we kill idfs-2 (middle instance) as planned.
        results.add(new ServiceFailoverTest(
                "IDFS",
                pidsDir + "/idfs-2.pid",
                List.of("http://localhost:8080",
                        "http://localhost:8081",
                        "http://localhost:8082"),
                clusterManagerUrl,
                false
        ).run());

        pauseBetweenTests();

        // ── 5. EPS (special: restarts on same port) ───────────────────────────
        results.add(new ServiceFailoverTest(
                "EPS",
                pidsDir + "/eps-2.pid",
                List.of("http://localhost:9003",
                        "http://localhost:9103",
                        "http://localhost:9203"),
                clusterManagerUrl,
                true  // epsMode — asserts recovery on same port, not new URL
        ).run());

        pauseBetweenTests();

        // ── 6. Notification ───────────────────────────────────────────────────
        results.add(new ServiceFailoverTest(
                "Notification",
                pidsDir + "/notify-2.pid",
                List.of("http://localhost:9004",
                        "http://localhost:9014",
                        "http://localhost:9024"),
                clusterManagerUrl,
                false
        ).run());

        pauseBetweenTests();

        // ── 7. VSS ────────────────────────────────────────────────────────────
        results.add(new ServiceFailoverTest(
                "VSS",
                pidsDir + "/vss-2.pid",
                List.of("http://localhost:9005",
                        "http://localhost:9015",
                        "http://localhost:9025"),
                clusterManagerUrl,
                false
        ).run());

        // ── Summary ───────────────────────────────────────────────────────────
        printSummary(results);
    }

    // =========================================================================
    // Pre-flight checks
    // =========================================================================

    private static boolean checkClusterManager(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/health"))
                    .GET().timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkPidFiles(String pidsDir) {
        String[] required = {
            "storage-2.pid", "ums-2.pid", "dms-2.pid",
            "idfs-2.pid", "eps-2.pid", "notify-2.pid", "vss-2.pid"
        };
        boolean allPresent = true;
        for (String file : required) {
            Path p = Path.of(pidsDir + "/" + file);
            if (!Files.exists(p)) {
                System.out.println("  ❌ Missing PID file: " + p);
                allPresent = false;
            }
        }
        return allPresent;
    }

    // =========================================================================
    // Summary
    // =========================================================================

    private static void printSummary(List<FailoverResult> results) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SUMMARY                               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        int passed = 0, failed = 0;
        for (FailoverResult r : results) {
            String icon   = r.passed() ? "✅" : "❌";
            String status = r.passed() ? "PASS" : "FAIL";
            String timing = r.durationMs() / 1000 + "s";
            System.out.printf("║  %s  %-14s  %-6s  %-30s  ║%n",
                    icon, r.serviceName(), status,
                    r.passed() ? "(" + timing + ")" : r.message().substring(0, Math.min(r.message().length(), 28)));
            if (r.passed()) passed++; else failed++;
        }

        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Total: %d passed, %d failed%-32s║%n",
                passed, failed, "");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        if (failed > 0) {
            System.out.println("Failed services:");
            for (FailoverResult r : results) {
                if (!r.passed()) {
                    System.out.println("  " + r.serviceName() + ": " + r.message());
                }
            }
        }

        System.exit(failed > 0 ? 1 : 0);
    }

    /**
     * Brief pause between tests so the platform can stabilize after
     * each restart before we start stressing the next service.
     */
    private static void pauseBetweenTests() throws InterruptedException {
        System.out.println("\n  [Pausing 10s before next test...]\n");
        Thread.sleep(10_000);
    }
}
