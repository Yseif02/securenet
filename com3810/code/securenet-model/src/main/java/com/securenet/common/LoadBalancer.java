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
import java.util.logging.Logger;

/**
 * Round-robin load balancer with periodic health checks for stateless
 * SecureNet services.
 *
 * <p>Implements DS Problem #3 (Load Balancing) and DS Problem #4
 * (Failure Detection). Distributes traffic round-robin across healthy
 * instances, automatically removing and re-adding instances as their
 * health status changes.
 */
public class LoadBalancer {

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    private final String serviceName;
    private final List<String> allUrls;
    private final Set<String> healthyUrls;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final HttpClient httpClient;
    private final ScheduledExecutorService healthChecker;

    private static final int HEALTH_CHECK_INTERVAL_MS = 5000;
    private static final int HEALTH_CHECK_TIMEOUT_MS  = 2000;

    public LoadBalancer(String serviceName, List<String> instanceUrls) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.allUrls     = List.copyOf(instanceUrls);
        this.healthyUrls = ConcurrentHashMap.newKeySet();
        this.healthyUrls.addAll(allUrls);
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(HEALTH_CHECK_TIMEOUT_MS))
                .build();
        this.healthChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lb-health-" + serviceName);
            t.setDaemon(true);
            return t;
        });
    }

    public LoadBalancer(String serviceName, String singleUrl) {
        this(serviceName, List.of(singleUrl));
    }

    public void start() {
        healthChecker.scheduleAtFixedRate(this::runHealthChecks,
                HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        log.info("[LB:" + serviceName + "] Started with " + allUrls.size()
                + " instances: " + allUrls);
    }

    public void stop() {
        healthChecker.shutdownNow();
        log.info("[LB:" + serviceName + "] Stopped");
    }

    public String nextHealthyUrl() {
        List<String> healthy = List.copyOf(healthyUrls);
        if (healthy.isEmpty()) {
            log.severe("[LB:" + serviceName + "] No healthy instances available");
            throw new RuntimeException("No healthy instances for " + serviceName);
        }
        int idx = Math.abs(roundRobinIndex.getAndIncrement() % healthy.size());
        String selected = healthy.get(idx);
        log.fine("[LB:" + serviceName + "] Selected " + selected
                + " (" + healthy.size() + " healthy)");
        return selected;
    }

    public int healthyCount() { return healthyUrls.size(); }

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
                        log.info("[LB:" + serviceName + "] " + url + " recovered → healthy"
                                + " (healthy count: " + healthyUrls.size() + ")");
                    }
                } else {
                    if (healthyUrls.remove(url)) {
                        log.warning("[LB:" + serviceName + "] " + url
                                + " unhealthy (HTTP " + resp.statusCode() + ")"
                                + " (healthy count: " + healthyUrls.size() + ")");
                    }
                }
            } catch (Exception e) {
                if (healthyUrls.remove(url)) {
                    log.warning("[LB:" + serviceName + "] " + url
                            + " unreachable: " + e.getMessage()
                            + " (healthy count: " + healthyUrls.size() + ")");
                }
            }
        }
    }
}