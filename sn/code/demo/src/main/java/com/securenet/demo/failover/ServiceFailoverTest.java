package com.securenet.demo.failover;

import com.securenet.common.LoadBalancer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reusable failover test for a single load-balanced service.
 *
 * <p>Uses the real {@link LoadBalancer} + ClusterManager polling. Sends
 * HTTP GET /health requests and tracks which port responded to verify
 * round-robin distribution and post-restart discovery.
 *
 * <h3>Test phases</h3>
 * <ol>
 *   <li>Verify all 3 instances healthy</li>
 *   <li>Send N requests → confirm all 3 ports got traffic</li>
 *   <li>Kill instance-2 via its PID file</li>
 *   <li>Wait for LB to drop it (healthy count drops to 2)</li>
 *   <li>Send N requests → confirm only 2 ports get traffic</li>
 *   <li>Wait for ClusterManager to detect failure + restart (healthy count back to 3)</li>
 *   <li>Send N requests → confirm 3 distinct ports get traffic</li>
 * </ol>
 */
public class ServiceFailoverTest {

    private static final int REQUESTS_PER_PHASE = 9;
    private static final int LB_DROP_TIMEOUT_MS = 20_000;
    private static final int RESTART_TIMEOUT_MS = 90_000; // ClusterManager: 15s threshold + restart time
    private static final int POLL_INTERVAL_MS   = 500;

    private final String serviceName;
    private final String pidFile2;       // path to pids/<service>-2.pid
    private final List<String> allUrls;  // all 3 original URLs
    private final String clusterManagerUrl;
    private final boolean epsMode;       // EPS restarts on same port — different assertion

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public ServiceFailoverTest(String serviceName,
                               String pidFile2,
                               List<String> allUrls,
                               String clusterManagerUrl,
                               boolean epsMode) {
        this.serviceName       = serviceName;
        this.pidFile2          = pidFile2;
        this.allUrls           = allUrls;
        this.clusterManagerUrl = clusterManagerUrl;
        this.epsMode           = epsMode;
    }

    public FailoverResult run() {
        long start = System.currentTimeMillis();
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        print("  Testing: " + serviceName + " failover");
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Build a load balancer for this service, watching ClusterManager
        LoadBalancer lb = new LoadBalancer(serviceName,
                new ArrayList<>(allUrls));
        lb.watchClusterManager(clusterManagerUrl, serviceName);
        lb.start();

        try {
            // ── Phase 1: All 3 healthy ────────────────────────────────────
            print("\n[Phase 1] Verifying all 3 instances healthy...");
            String err = waitForHealthyCount(lb, 3, 15_000);
            if (err != null) return fail(start, "Phase 1 failed: " + err);
            print("  ✓ All 3 instances healthy");

            // ── Phase 2: Round-robin across all 3 ────────────────────────
            print("\n[Phase 2] Sending " + REQUESTS_PER_PHASE + " requests — expecting all 3 ports...");
            Map<String, AtomicInteger> counts = sendRequests(lb, REQUESTS_PER_PHASE);
            print("  Port hit counts: " + formatCounts(counts));
            if (counts.size() < 3)
                return fail(start, "Phase 2 failed: only " + counts.size()
                        + " ports received traffic, expected 3. Counts: " + formatCounts(counts));
            print("  ✓ All 3 ports received traffic");

            // ── Phase 3: Kill instance-2 ──────────────────────────────────
            print("\n[Phase 3] Killing " + serviceName + "-2 (PID file: " + pidFile2 + ")...");
            String killErr = killInstance(pidFile2);
            if (killErr != null) return fail(start, "Phase 3 failed: " + killErr);
            print("  ✓ Kill signal sent");

            // ── Phase 4: Wait for LB to drop it ──────────────────────────
            print("\n[Phase 4] Waiting for LB to detect failure (up to "
                    + LB_DROP_TIMEOUT_MS / 1000 + "s)...");
            err = waitForHealthyCount(lb, 2, LB_DROP_TIMEOUT_MS);
            if (err != null) return fail(start, "Phase 4 failed: " + err);
            print("  ✓ LB dropped failed instance, 2 healthy remaining");

            // ── Phase 5: Only 2 ports get traffic ────────────────────────
            print("\n[Phase 5] Sending " + REQUESTS_PER_PHASE + " requests — expecting only 2 ports...");
            counts = sendRequests(lb, REQUESTS_PER_PHASE);
            print("  Port hit counts: " + formatCounts(counts));
            if (counts.size() != 2)
                return fail(start, "Phase 5 failed: " + counts.size()
                        + " ports received traffic, expected 2. Counts: " + formatCounts(counts));
            print("  ✓ Only 2 ports received traffic (failed instance excluded)");

            // ── Phase 6: Wait for ClusterManager restart ──────────────────
            if (epsMode) {
                // EPS restarts on same port — wait for healthy count to recover to 3
                print("\n[Phase 6] EPS mode: waiting for port to recover on same URL (up to "
                        + RESTART_TIMEOUT_MS / 1000 + "s)...");
                err = waitForHealthyCount(lb, 3, RESTART_TIMEOUT_MS);
                if (err != null) return fail(start, "Phase 6 (EPS) failed — port did not recover: " + err);
                print("  ✓ EPS instance recovered on original port");
            } else {
                // Standard services: wait for a NEW url to appear (healthy count back to 3)
                print("\n[Phase 6] Waiting for ClusterManager to restart on new port (up to "
                        + RESTART_TIMEOUT_MS / 1000 + "s)...");
                Set<String> originalUrls = new HashSet<>(allUrls);
                err = waitForNewInstance(lb, originalUrls, RESTART_TIMEOUT_MS);
                if (err != null) return fail(start, "Phase 6 failed — no new instance discovered: " + err);
                print("  ✓ LB discovered restarted instance on new port");
            }

            // ── Phase 7: All 3 ports get traffic again ────────────────────
            print("\n[Phase 7] Sending " + REQUESTS_PER_PHASE + " requests — expecting 3 ports again...");
            counts = sendRequests(lb, REQUESTS_PER_PHASE);
            print("  Port hit counts: " + formatCounts(counts));
            if (counts.size() < 3)
                return fail(start, "Phase 7 failed: only " + counts.size()
                        + " ports received traffic after restart. Counts: " + formatCounts(counts));
            print("  ✓ All 3 ports (including restarted) received traffic");

            long elapsed = System.currentTimeMillis() - start;
            print("\n  ✅ " + serviceName + " PASSED (" + elapsed / 1000 + "s)");
            return FailoverResult.pass(serviceName, "All 7 phases passed", elapsed);

        } catch (Exception e) {
            return fail(start, "Unexpected exception: " + e.getMessage());
        } finally {
            lb.stop();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Sends N GET /health requests through the load balancer and counts
     * how many times each port was selected.
     */
    private Map<String, AtomicInteger> sendRequests(LoadBalancer lb, int count) {
        Map<String, AtomicInteger> portCounts = new ConcurrentHashMap<>();
        for (int i = 0; i < count; i++) {
            try {
                String url = lb.nextHealthyUrl();
                // Extract port from URL for display
                String port = url.replaceAll(".*:(\\d+)$", "$1");
                portCounts.computeIfAbsent(port, k -> new AtomicInteger()).incrementAndGet();

                // Actually send the request so the LB health check also sees traffic
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/health"))
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.ofString());

                Thread.sleep(50); // small gap to avoid burst
            } catch (Exception e) {
                // LB may throw if no healthy instances — count as a miss
                portCounts.computeIfAbsent("ERROR", k -> new AtomicInteger()).incrementAndGet();
            }
        }
        return portCounts;
    }

    /** Waits until lb.healthyCount() == target, up to timeoutMs. */
    private String waitForHealthyCount(LoadBalancer lb, int target, int timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int count = lb.healthyCount();
            if (count == target) return null;
            System.out.print(".");
            System.out.flush();
            Thread.sleep(POLL_INTERVAL_MS);
        }
        System.out.println();
        return "Timed out after " + timeoutMs / 1000 + "s waiting for "
                + target + " healthy instances (current: " + lb.healthyCount() + ")";
    }

    /**
     * Waits until a URL not in originalUrls appears in lb.getStatus() and is healthy.
     * This is the signal that ClusterManager restarted on a new port and LB discovered it.
     */
    private String waitForNewInstance(LoadBalancer lb, Set<String> originalUrls, int timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Boolean> status = lb.getStatus();
            for (Map.Entry<String, Boolean> e : status.entrySet()) {
                // URL format is http://localhost:PORT — reconstruct to compare
                if (!originalUrls.contains(e.getKey()) && Boolean.TRUE.equals(e.getValue())) {
                    System.out.println();
                    print("  New instance discovered: " + e.getKey());
                    return null; // found a new healthy URL
                }
            }
            // Also accept: healthy count back to 3 with any URLs
            // (covers edge case where ClusterManager reuses the same port)
            if (lb.healthyCount() >= 3) {
                System.out.println();
                return null;
            }
            System.out.print(".");
            System.out.flush();
            Thread.sleep(POLL_INTERVAL_MS);
        }
        System.out.println();
        return "Timed out after " + timeoutMs / 1000 + "s. LB status: " + lb.getStatus();
    }

    /** Reads PID from file and sends SIGTERM. */
    private String killInstance(String pidFilePath) {
        try {
            Path path = Path.of(pidFilePath);
            if (!Files.exists(path)) {
                return "PID file not found: " + pidFilePath;
            }
            String pidStr = Files.readString(path).trim();
            long pid = Long.parseLong(pidStr);

            // Use ProcessHandle for clean cross-platform kill
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty()) {
                return "Process " + pid + " not found (already dead?)";
            }
            handle.get().destroy(); // SIGTERM
            print("  Sent SIGTERM to PID " + pid);
            return null;
        } catch (Exception e) {
            return "Failed to kill instance: " + e.getMessage();
        }
    }

    private FailoverResult fail(long start, String message) {
        long elapsed = System.currentTimeMillis() - start;
        print("\n  ❌ " + serviceName + " FAILED: " + message);
        return FailoverResult.fail(serviceName, message, elapsed);
    }

    private static String formatCounts(Map<String, AtomicInteger> counts) {
        StringBuilder sb = new StringBuilder("{");
        counts.forEach((port, count) ->
                sb.append("port ").append(port).append("=").append(count.get()).append(", "));
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }

    private static void print(String msg) {
        System.out.println(msg);
    }
}
