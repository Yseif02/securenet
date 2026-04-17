package com.securenet.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancer with periodic health checks for stateless
 * SecureNet services.
 *
 * <p>Implements DS Problem #3 (Load Balancing) from the Stage 3 design
 * document. Distributes traffic using round-robin across healthy
 * instances. Unhealthy instances are automatically removed from
 * rotation and re-added when they recover.
 *
 * <p>Implements DS Problem #4 (Failure Detection) — performs periodic
 * HTTP health checks on all registered instances. An instance that
 * fails to respond to the health endpoint is marked unhealthy and
 * removed from the active pool.
 *
 * <p>Usage:
 * <pre>{@code
 * LoadBalancer dmsLb = new LoadBalancer("DMS",
 *     List.of("http://localhost:9002", "http://localhost:9012"));
 * dmsLb.start();
 *
 * String url = dmsLb.nextHealthyUrl(); // round-robin among healthy instances
 * }</pre>
 *
 * @see com.securenet.gateway.APIGatewayService
 */
public class LoadBalancer {

    private final String serviceName;
    private final List<String> allUrls;
    private final Set<String> healthyUrls;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final HttpClient httpClient;
    private final ScheduledExecutorService healthChecker;

    private static final int HEALTH_CHECK_INTERVAL_MS = 5000;
    private static final int HEALTH_CHECK_TIMEOUT_MS = 2000;

    /**
     * @param serviceName human-readable name for logging
     * @param instanceUrls list of base URLs for all known instances
     */
    public LoadBalancer(String serviceName, List<String> instanceUrls) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.allUrls = List.copyOf(instanceUrls);
        this.healthyUrls = ConcurrentHashMap.newKeySet();
        this.healthyUrls.addAll(allUrls);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(HEALTH_CHECK_TIMEOUT_MS))
                .build();
        this.healthChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lb-health-" + serviceName);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Convenience constructor for a single instance (no load balancing
     * needed, but keeps the API uniform).
     */
    public LoadBalancer(String serviceName, String singleUrl) {
        this(serviceName, List.of(singleUrl));
    }

    /** Starts periodic health checks. */
    public void start() {
        healthChecker.scheduleAtFixedRate(this::runHealthChecks,
                HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    /** Stops health checks. */
    public void stop() {
        healthChecker.shutdownNow();
    }

    /**
     * Returns the next healthy instance URL using round-robin.
     *
     * @return base URL of a healthy instance
     * @throws RuntimeException if no healthy instances are available
     */
    public String nextHealthyUrl() {
        List<String> healthy = List.copyOf(healthyUrls);
        if (healthy.isEmpty()) {
            throw new RuntimeException("No healthy instances for " + serviceName);
        }
        int idx = Math.abs(roundRobinIndex.getAndIncrement() % healthy.size());
        return healthy.get(idx);
    }

    /** Returns the number of currently healthy instances. */
    public int healthyCount() {
        return healthyUrls.size();
    }

    /** Returns all instance URLs and their health status. */
    public Map<String, Boolean> getStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (String url : allUrls) {
            status.put(url, healthyUrls.contains(url));
        }
        return status;
    }

    private void runHealthChecks() {
        for (String url : allUrls) {
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
                        System.out.println("[LB:" + serviceName + "] " + url + " recovered");
                    }
                } else {
                    if (healthyUrls.remove(url)) {
                        System.out.println("[LB:" + serviceName + "] " + url +
                                " unhealthy (HTTP " + resp.statusCode() + ")");
                    }
                }
            } catch (Exception e) {
                if (healthyUrls.remove(url)) {
                    System.out.println("[LB:" + serviceName + "] " + url +
                            " unreachable: " + e.getMessage());
                }
            }
        }
    }
}
