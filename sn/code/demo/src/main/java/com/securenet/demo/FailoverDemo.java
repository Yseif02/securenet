package com.securenet.demo;

import com.securenet.demo.failover.FailoverResult;
import com.securenet.demo.failover.ServiceFailoverTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Failover Demo — verifies that load balancers discover restarted service
 * instances on new ports via ClusterManager polling.
 *
 * <p>Does NOT use PID files. Instance selection is done entirely via the
 * ClusterManager status endpoint, so the demo works correctly on repeated
 * runs without restarting the platform.
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>Full platform running: {@code ./scripts/start-platform.sh}</li>
 *   <li>ClusterManager up at {@code localhost:9090}</li>
 *   <li>At least 30s since startup (ClusterManager initial delay)</li>
 * </ul>
 *
 * <h3>Run</h3>
 * <pre>
 * java -cp "$(find . -name '*.jar' -path '*\/target\/*' | grep -v sources | tr '\n' ':')" \
 *   com.securenet.demo.FailoverDemo [--cluster-manager http://localhost:9090]
 * </pre>
 */
public class FailoverDemo {

    private static final String DEFAULT_CLUSTER_MANAGER = "http://localhost:9090";

    public static void main(String[] args) throws Exception {
        String clusterManagerUrl = DEFAULT_CLUSTER_MANAGER;

        for (int i = 0; i < args.length; i++) {
            if ("--cluster-manager".equals(args[i])) clusterManagerUrl = args[++i];
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          SecureNet — Load Balancer Failover Demo             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  ClusterManager: " + clusterManagerUrl);
        System.out.println();

        if (!checkClusterManager(clusterManagerUrl)) {
            System.out.println("❌ Cannot reach ClusterManager at " + clusterManagerUrl);
            System.out.println("   Make sure the platform is running: ./scripts/start-platform.sh");
            System.exit(1);
        }
        System.out.println("  ✓ ClusterManager reachable");
        System.out.println();
        System.out.println("  NOTE: Each test kills a service instance and waits up to 90s");
        System.out.println("        for ClusterManager to detect + restart it. Total runtime");
        System.out.println("        can be 10-15 minutes for all 7 services.");
        System.out.println("        The demo can be run repeatedly without restarting the platform.");
        System.out.println();

        List<FailoverResult> results = new ArrayList<>();

        results.add(new ServiceFailoverTest(
                "Storage",
                List.of("http://localhost:9000",
                        "http://localhost:9010",
                        "http://localhost:9020"),
                clusterManagerUrl, false
        ).run());
        pauseBetweenTests();

        results.add(new ServiceFailoverTest(
                "UMS",
                List.of("http://localhost:9001",
                        "http://localhost:9011",
                        "http://localhost:9021"),
                clusterManagerUrl, false
        ).run());
        pauseBetweenTests();

        results.add(new ServiceFailoverTest(
                "DMS",
                List.of("http://localhost:9002",
                        "http://localhost:9012",
                        "http://localhost:9022"),
                clusterManagerUrl, false
        ).run());
        pauseBetweenTests();

        // IDFS: idfs-1 hosts the MQTT broker — primary is lowest port (8080),
        // which is correctly skipped by the kill selection logic.
        results.add(new ServiceFailoverTest(
                "IDFS",
                List.of("http://localhost:8080",
                        "http://localhost:8081",
                        "http://localhost:8082"),
                clusterManagerUrl, false
        ).run());
        pauseBetweenTests();

        // EPS: restarts on the same port (Raft constraint)
        results.add(new ServiceFailoverTest(
                "EPS",
                List.of("http://localhost:9003",
                        "http://localhost:9103",
                        "http://localhost:9203"),
                clusterManagerUrl, true
        ).run());
        pauseBetweenTests();

        results.add(new ServiceFailoverTest(
                "Notification",
                List.of("http://localhost:9004",
                        "http://localhost:9014",
                        "http://localhost:9024"),
                clusterManagerUrl, false
        ).run());
        pauseBetweenTests();

        results.add(new ServiceFailoverTest(
                "VSS",
                List.of("http://localhost:9005",
                        "http://localhost:9015",
                        "http://localhost:9025"),
                clusterManagerUrl, false
        ).run());

        printSummary(results);
    }

    private static boolean checkClusterManager(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/health"))
                    .GET().timeout(Duration.ofSeconds(3)).build();
            return client.send(req, HttpResponse.BodyHandlers.ofString())
                    .statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void printSummary(List<FailoverResult> results) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SUMMARY                               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        int passed = 0, failed = 0;
        for (FailoverResult r : results) {
            String icon   = r.passed() ? "✅" : "❌";
            String status = r.passed() ? "PASS" : "FAIL";
            String detail = r.passed()
                    ? "(" + r.durationMs() / 1000 + "s)"
                    : r.message().substring(0, Math.min(r.message().length(), 28));
            System.out.printf("║  %s  %-14s  %-6s  %-30s  ║%n",
                    icon, r.serviceName(), status, detail);
            if (r.passed()) passed++; else failed++;
        }
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Total: %d passed, %d failed%-32s║%n", passed, failed, "");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        if (failed > 0) {
            System.out.println("Failed services:");
            for (FailoverResult r : results) {
                if (!r.passed()) System.out.println("  " + r.serviceName() + ": " + r.message());
            }
        }
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void pauseBetweenTests() throws InterruptedException {
        System.out.println("\n  [Pausing 10s before next test...]\n");
        Thread.sleep(10_000);
    }
}