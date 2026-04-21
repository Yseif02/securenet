package com.securenet.demo.failover;

import com.securenet.common.JsonUtil;
import com.securenet.common.LoadBalancer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * <p>Instance selection for killing is done by querying the ClusterManager
 * status endpoint directly — not by reading PID files. This means the test
 * works correctly on repeated runs even after instances have been restarted
 * on new ports by the ClusterManager.
 *
 * <h3>Kill selection strategy</h3>
 * <p>Queries {@code GET /cluster/status}, collects all HEALTHY URLs for the
 * target service, sorts by port ascending, skips the lowest-port instance
 * (treated as primary — e.g. idfs-1 hosts the MQTT broker), and picks the
 * next one. The process listening on that port is found via {@code lsof}
 * and sent SIGTERM.
 *
 * <h3>Test phases</h3>
 * <ol>
 *   <li>Verify all 3 instances healthy</li>
 *   <li>Send N requests → confirm all 3 ports got traffic</li>
 *   <li>Query ClusterManager → pick a non-primary instance → kill it</li>
 *   <li>Wait for LB to drop it (healthy count drops to 2)</li>
 *   <li>Send N requests → confirm only 2 ports get traffic</li>
 *   <li>Wait for ClusterManager to detect failure + restart</li>
 *   <li>Send N requests → confirm 3 distinct ports get traffic</li>
 * </ol>
 */
public class ServiceFailoverTest {

    private static final int REQUESTS_PER_PHASE = 9;
    private static final int LB_DROP_TIMEOUT_MS = 20_000;
    private static final int RESTART_TIMEOUT_MS = 90_000;
    private static final int POLL_INTERVAL_MS   = 500;

    private final String serviceName;
    private final List<String> allUrls;
    private final String clusterManagerUrl;
    private final boolean epsMode;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /**
     * @param serviceName       name matching ClusterManager registration (e.g. "DMS")
     * @param allUrls           initial 3 URLs to seed the LoadBalancer
     * @param clusterManagerUrl base URL of ClusterManager (e.g. "http://localhost:9090")
     * @param epsMode           true for EPS — restarts on same port, different phase 6 assertion
     */
    public ServiceFailoverTest(String serviceName,
                               List<String> allUrls,
                               String clusterManagerUrl,
                               boolean epsMode) {
        this.serviceName       = serviceName;
        this.allUrls           = allUrls;
        this.clusterManagerUrl = clusterManagerUrl;
        this.epsMode           = epsMode;
    }

    public FailoverResult run() {
        long start = System.currentTimeMillis();
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        print("  Testing: " + serviceName + " failover");
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        LoadBalancer lb = new LoadBalancer(serviceName, new ArrayList<>(allUrls));
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

            // ── Phase 3: Select and kill a non-primary instance ───────────
            print("\n[Phase 3] Querying ClusterManager to select a non-primary instance to kill...");
            KillTarget target = findKillTarget();
            if (target == null)
                return fail(start, "Phase 3 failed: could not find a killable instance "
                        + "for " + serviceName + " in ClusterManager status");
            print("  Selected: " + target.instanceId() + " at " + target.url()
                    + " (PID " + target.pid() + ")");

            String killErr = killProcess(target.pid());
            if (killErr != null) return fail(start, "Phase 3 failed: " + killErr);
            print("  ✓ Kill signal sent to " + target.instanceId() + " on " + target.url());

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
                // EPS restarts on same port — wait for the killed URL to recover
                String killedUrl = target.url();
                print("\n[Phase 6] EPS mode: waiting for " + killedUrl
                        + " to recover (up to " + RESTART_TIMEOUT_MS / 1000 + "s)...");
                err = waitForUrlRecovery(lb, killedUrl, RESTART_TIMEOUT_MS);
                if (err != null) return fail(start, "Phase 6 (EPS) failed: " + err);
                print("  ✓ EPS instance recovered on " + killedUrl);
            } else {
                // Standard services: wait for a NEW url to appear
                // Snapshot current known URLs before waiting so we detect genuinely new ones
                Set<String> urlsBeforeKill = new HashSet<>(lb.getStatus().keySet());
                print("\n[Phase 6] Waiting for ClusterManager to restart on new port (up to "
                        + RESTART_TIMEOUT_MS / 1000 + "s)...");
                err = waitForNewInstance(lb, urlsBeforeKill, RESTART_TIMEOUT_MS);
                if (err != null) return fail(start, "Phase 6 failed: " + err);
                print("  ✓ LB discovered restarted instance");
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
    // ClusterManager-based kill target selection
    // =========================================================================

    /**
     * Queries ClusterManager status, collects all HEALTHY instances for this
     * service, sorts by port ascending, skips the lowest-port instance
     * (the "primary"), and returns the next one as the kill target.
     *
     * <p>Works across repeated runs because ClusterManager tracks the current
     * URL of each instance regardless of restarts.
     */
    @SuppressWarnings("unchecked")
    private KillTarget findKillTarget() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(clusterManagerUrl + "/cluster/status"))
                    .GET().timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            Map<String, Map<String, Object>> status =
                    (Map<String, Map<String, Object>>) JsonUtil.fromJson(
                            resp.body(), Map.class);

            // Collect all HEALTHY instances for our service
            List<CandidateInstance> candidates = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : status.entrySet()) {
                Map<String, Object> inst = entry.getValue();
                if (!serviceName.equals(inst.get("service"))) continue;
                if (!"HEALTHY".equals(inst.get("status"))) continue;
                String url = (String) inst.get("url");
                int port = Integer.parseInt(url.replaceAll(".*:(\\d+)$", "$1"));
                candidates.add(new CandidateInstance(entry.getKey(), url, port));
            }

            if (candidates.size() < 2) {
                print("  Only " + candidates.size()
                        + " healthy instance(s) found — need at least 2 to safely kill one");
                return null;
            }

            // Sort by port ascending — lowest port is the primary (skip it)
            candidates.sort(Comparator.comparingInt(CandidateInstance::port));

            print("  Healthy instances found: " + candidates.stream()
                    .map(c -> c.instanceId() + "@" + c.port())
                    .collect(java.util.stream.Collectors.joining(", ")));

            // Pick the second-lowest port — not primary, leaves primary + one other healthy
            CandidateInstance chosen = candidates.get(1);

            // Find PID via lsof
            long pid = findPidOnPort(chosen.port());
            if (pid < 0) {
                print("  Could not find PID on port " + chosen.port()
                        + " via lsof — is the process running?");
                return null;
            }

            return new KillTarget(chosen.instanceId(), chosen.url(), pid);

        } catch (Exception e) {
            print("  findKillTarget error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Uses {@code lsof -ti :<port>} to find the PID listening on a port.
     * Returns -1 if not found or on error.
     */
    private long findPidOnPort(int port) {
        try {
            Process p = new ProcessBuilder(
                    "lsof", "-ti", ":" + port, "-sTCP:LISTEN").start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (output.isEmpty()) return -1;
            // lsof may return multiple lines (IPv4 + IPv6) — take the first valid PID
            for (String line : output.split("\\s+")) {
                try { return Long.parseLong(line.trim()); } catch (NumberFormatException ignore) {}
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Sends SIGTERM to the given PID. */
    private String killProcess(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) return "Process " + pid + " not found (already dead?)";
        handle.get().destroy();
        return null;
    }

    // =========================================================================
    // Request sending
    // =========================================================================

    private Map<String, AtomicInteger> sendRequests(LoadBalancer lb, int count) {
        Map<String, AtomicInteger> portCounts = new ConcurrentHashMap<>();
        for (int i = 0; i < count; i++) {
            try {
                String url = lb.nextHealthyUrl();
                String port = url.replaceAll(".*:(\\d+)$", "$1");
                portCounts.computeIfAbsent(port, k -> new AtomicInteger()).incrementAndGet();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/health"))
                        .GET().timeout(Duration.ofSeconds(2))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.ofString());
                Thread.sleep(50);
            } catch (Exception e) {
                portCounts.computeIfAbsent("ERROR", k -> new AtomicInteger()).incrementAndGet();
            }
        }
        return portCounts;
    }

    // =========================================================================
    // Wait helpers
    // =========================================================================

    private String waitForHealthyCount(LoadBalancer lb, int target, int timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (lb.healthyCount() == target) return null;
            System.out.print(".");
            System.out.flush();
            Thread.sleep(POLL_INTERVAL_MS);
        }
        System.out.println();
        return "Timed out after " + timeoutMs / 1000 + "s waiting for "
                + target + " healthy instances (current: " + lb.healthyCount() + ")";
    }

    private String waitForNewInstance(LoadBalancer lb, Set<String> urlsBeforeKill,
                                      int timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Boolean> lbStatus = lb.getStatus();
            for (Map.Entry<String, Boolean> e : lbStatus.entrySet()) {
                if (!urlsBeforeKill.contains(e.getKey())
                        && Boolean.TRUE.equals(e.getValue())) {
                    System.out.println();
                    print("  New instance discovered: " + e.getKey());
                    return null;
                }
            }
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

    private String waitForUrlRecovery(LoadBalancer lb, String targetUrl,
                                      int timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(lb.getStatus().get(targetUrl))) {
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

    // =========================================================================
    // Internal types
    // =========================================================================

    private record CandidateInstance(String instanceId, String url, int port) {}
    private record KillTarget(String instanceId, String url, long pid) {}

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

    private static void print(String msg) { System.out.println(msg); }
}