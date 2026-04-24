package com.securenet.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Round-robin load balancer with periodic health checks for stateless
 * SecureNet services.
 *
 * <p>Implements DS Problem #3 (Load Balancing) and DS Problem #4
 * (Failure Detection). Distributes traffic round-robin across healthy
 * instances, automatically removing and re-adding instances as their
 * health status changes.
 *
 * <h3>Dynamic instance discovery</h3>
 * <p>Call {@link #watchClusterManager(String, String)} after construction
 * to enable background polling of the ClusterManager's
 * {@code /cluster/status} endpoint. When the ClusterManager restarts a
 * failed instance on a new port, the load balancer will discover the new
 * URL within one poll interval (~5 s) and add it to rotation. Instances
 * that disappear from the ClusterManager's status (FAILED or REPLACED)
 * are removed from {@code knownUrls} so they are never retried.
 */
public class LoadBalancer {

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    private static final int HEALTH_CHECK_INTERVAL_MS = 5000;
    private static final int HEALTH_CHECK_TIMEOUT_MS  = 2000;

    private final String serviceName;

    /**
     * All URLs this load balancer currently knows about — mutable so that
     * {@link #watchClusterManager} can add/remove instances at runtime.
     * Replaces the old immutable {@code List<String> allUrls}.
     */
    private final Set<String> knownUrls;

    /** Subset of knownUrls that passed their last health check. */
    private final Set<String> healthyUrls;

    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private volatile List<String> watchedClusterManagerUrls = List.of();

    // =========================================================================
    // Construction
    // =========================================================================

    public LoadBalancer(String serviceName, List<String> instanceUrls) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.knownUrls   = ConcurrentHashMap.newKeySet();
        this.knownUrls.addAll(instanceUrls);
        this.healthyUrls = ConcurrentHashMap.newKeySet();
        this.healthyUrls.addAll(instanceUrls); // optimistic — health check will confirm
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(HEALTH_CHECK_TIMEOUT_MS))
                .build();
        this.scheduler   = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "lb-" + serviceName);
            t.setDaemon(true);
            return t;
        });
    }

    public LoadBalancer(String serviceName, String singleUrl) {
        this(serviceName, List.of(singleUrl));
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Starts the periodic health-check loop.
     * Must be called before {@link #nextHealthyUrl()}.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::runHealthChecks,
                HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        log.info("[LB:" + serviceName + "] Started with " + knownUrls.size()
                + " instances: " + knownUrls);
    }

    /**
     * Enables background polling of the ClusterManager status endpoint so
     * that newly restarted instances are discovered and added to rotation
     * automatically.
     *
     * <p>The poll runs on the same scheduler thread as health checks, at the
     * same interval, so no extra threads are created. The ClusterManager
     * service name in its status JSON must match {@code serviceName} exactly
     * (e.g. "DMS", "UMS", "Storage").
     *
     * <p>Call this <em>before</em> {@link #start()} so that the first poll
     * fires at the same time as the first health check.
     *
     * @param clusterManagerUrl base URL of the ClusterManager HTTP server,
     *                          e.g. {@code "http://localhost:9090"}
     * @param serviceName       service name to filter on in the status JSON —
     *                          must match the name registered in ClusterManager
     *                          (case-sensitive)
     */
    public void watchClusterManager(String clusterManagerUrl, String serviceName) {
        this.watchedClusterManagerUrls = Arrays.stream(clusterManagerUrl.split(","))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();
        scheduler.scheduleAtFixedRate(
                () -> pollClusterManager(serviceName),
                HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        log.info("[LB:" + serviceName + "] Watching ClusterManager at "
                + clusterManagerUrl + " for service=" + serviceName);
    }

    public void stop() {
        scheduler.shutdownNow();
        log.info("[LB:" + serviceName + "] Stopped");
    }

    // =========================================================================
    // Routing
    // =========================================================================

    /**
     * Returns the next healthy URL using round-robin selection.
     *
     * @throws RuntimeException if no healthy instances are available
     */
    public String nextHealthyUrl() {
        List<String> snapshot = List.copyOf(healthyUrls);
        if (snapshot.isEmpty()) {
            log.severe("[LB:" + serviceName + "] No healthy instances available");
            throw new RuntimeException("No healthy instances for " + serviceName);
        }
        int idx = Math.abs(roundRobinIndex.getAndIncrement() % snapshot.size());
        String selected = snapshot.get(idx);
        log.fine("[LB:" + serviceName + "] Selected " + selected
                + " (" + snapshot.size() + " healthy)");
        return selected;
    }

    public int healthyCount() {
        return healthyUrls.size();
    }

    /**
     * Returns a snapshot of all known URLs and whether each is currently healthy.
     */
    public Map<String, Boolean> getStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (String url : knownUrls) {
            status.put(url, healthyUrls.contains(url));
        }
        return status;
    }

    // =========================================================================
    // Health checks
    // =========================================================================

    private void runHealthChecks() {
        for (String url : Set.copyOf(knownUrls)) { // snapshot — knownUrls may change mid-loop
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/health"))
                        .GET()
                        .timeout(Duration.ofMillis(HEALTH_CHECK_TIMEOUT_MS))
                        .build();

                HttpResponse<String> resp = httpClient.send(req,
                        HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    if (healthyUrls.add(url)) {
                        log.info("[LB:" + serviceName + "] " + url
                                + " recovered → healthy"
                                + " (healthy: " + healthyUrls.size() + ")");
                    }
                } else {
                    if (healthyUrls.remove(url)) {
                        log.warning("[LB:" + serviceName + "] " + url
                                + " unhealthy (HTTP " + resp.statusCode() + ")"
                                + " (healthy: " + healthyUrls.size() + ")");
                    }
                }
            } catch (Exception e) {
                if (healthyUrls.remove(url)) {
                    log.warning("[LB:" + serviceName + "] " + url
                            + " unreachable: " + e.getMessage()
                            + " (healthy: " + healthyUrls.size() + ")");
                }
            }
        }
    }

    // =========================================================================
    // ClusterManager polling
    // =========================================================================

    /**
     * Polls {@code GET /cluster/status} and reconciles knownUrls against the
     * live ClusterManager view:
     * <ul>
     *   <li>URLs that appear in the status as HEALTHY or SUSPECTED are added
     *       to knownUrls if not already present (optimistically also added to
     *       healthyUrls — the next health-check cycle will confirm or remove).</li>
     *   <li>URLs that no longer appear, or appear as FAILED/REPLACED, are
     *       removed from both knownUrls and healthyUrls immediately.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private void pollClusterManager(String serviceName) {
        for (String clusterManagerUrl : watchedClusterManagerUrls) {
            if (pollSingleClusterManager(clusterManagerUrl, serviceName)) {
                return;
            }
        }
        log.warning("[LB:" + serviceName + "] ClusterManager poll failed for all URLs: "
                + watchedClusterManagerUrls);
    }

    @SuppressWarnings("unchecked")
    private boolean pollSingleClusterManager(String clusterManagerUrl, String serviceName) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(clusterManagerUrl + "/cluster/status"))
                    .GET()
                    .timeout(Duration.ofMillis(HEALTH_CHECK_TIMEOUT_MS))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warning("[LB:" + serviceName + "] ClusterManager returned HTTP "
                        + resp.statusCode());
                return false;
            }

            // Status JSON: { "instanceId": { "service":"DMS", "url":"http://...",
            //                                "status":"HEALTHY", ... }, ... }
            Map<String, Map<String, Object>> allInstances =
                    (Map<String, Map<String, Object>>) JsonUtil.fromJson(resp.body(), Map.class);

            // Collect URLs that the ClusterManager considers live for our service
            Set<String> clusterActiveUrls = new HashSet<>();
            for (Map<String, Object> entry : allInstances.values()) {
                if (!serviceName.equals(entry.get("service"))) continue;
                String status = (String) entry.get("status");
                if ("FAILED".equals(status) || "REPLACED".equals(status)) continue;
                String url = (String) entry.get("url");
                if (url != null) clusterActiveUrls.add(url);
            }

            // Add newly discovered instances
            for (String url : clusterActiveUrls) {
                if (knownUrls.add(url)) {
                    healthyUrls.add(url); // optimistic — health check will confirm shortly
                    log.info("[LB:" + serviceName + "] Discovered new instance from "
                            + "ClusterManager: " + url
                            + " (known: " + knownUrls.size() + ")");
                }
            }

            // Remove instances that are gone or failed
            for (String url : Set.copyOf(knownUrls)) {
                if (!clusterActiveUrls.contains(url)) {
                    knownUrls.remove(url);
                    healthyUrls.remove(url);
                    log.info("[LB:" + serviceName + "] Removed departed instance: " + url
                            + " (known: " + knownUrls.size() + ")");
                }
            }

            return true;
        } catch (Exception e) {
            log.warning("[LB:" + serviceName + "] ClusterManager poll failed at "
                    + clusterManagerUrl + ": " + e.getMessage());
            return false;
        }
    }
}
