package com.securenet.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Cluster Manager for SecureNet services.
 *
 * <p>Implements DS Problem #4 (Cluster Manager and Failure Detection)
 * from the Stage 3 design document. Responsibilities:
 *
 * <ul>
 *   <li>Continuously monitors health of all registered service instances</li>
 *   <li>Detects failures when an instance stops responding to health checks</li>
 *   <li>Logs failures and triggers restart callbacks</li>
 *   <li>Tracks instance lifecycle: HEALTHY → SUSPECTED → FAILED</li>
 * </ul>
 *
 * <p>The Cluster Manager itself is stateless and can run as multiple
 * instances behind a load balancer (it is not a single point of failure).
 *
 * <p>In a production deployment, the restart callback would call a
 * container orchestrator (e.g., start a new JVM process). In our
 * development environment, it executes a shell command to restart
 * the failed service.
 *
 * <h3>Failure detection</h3>
 * <p>Each instance is health-checked every {@code checkIntervalMs}.
 * If an instance fails to respond, it moves to SUSPECTED. After
 * {@code failureThresholdMs} of continuous non-response, it moves
 * to FAILED and the restart callback fires.
 */
public class ClusterManager {

    private final Map<String, ManagedInstance> instances = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;

    /** How often to check each instance (ms). */
    private final long checkIntervalMs;

    /** How long a SUSPECTED instance must be unresponsive before FAILED (ms). */
    private final long failureThresholdMs;

    /** Callback invoked when an instance is declared FAILED. */
    private final FailureCallback failureCallback;

    /**
     * Callback invoked when a managed instance transitions to FAILED.
     */
    @FunctionalInterface
    public interface FailureCallback {
        /**
         * @param serviceName logical name of the service (e.g. "DMS")
         * @param instanceId  unique identifier of the failed instance
         * @param url         the base URL of the failed instance
         */
        void onInstanceFailed(String serviceName, String instanceId, String url);
    }

    /**
     * @param checkIntervalMs     health check interval in milliseconds
     * @param failureThresholdMs  time before SUSPECTED → FAILED
     * @param failureCallback     callback when an instance fails
     */
    public ClusterManager(long checkIntervalMs, long failureThresholdMs,
                          FailureCallback failureCallback) {
        this.checkIntervalMs = checkIntervalMs;
        this.failureThresholdMs = failureThresholdMs;
        this.failureCallback = Objects.requireNonNull(failureCallback);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "cluster-manager");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Registers a service instance to be monitored.
     *
     * @param serviceName logical name (e.g. "UMS", "DMS", "EPS")
     * @param instanceId  unique identifier (e.g. "ums-1", "dms-2")
     * @param url         base URL for health checks (e.g. "http://localhost:9001")
     */
    public void registerInstance(String serviceName, String instanceId, String url) {
        instances.put(instanceId, new ManagedInstance(
                serviceName, instanceId, url, InstanceStatus.HEALTHY,
                System.currentTimeMillis()));
        System.out.println("[ClusterManager] Registered " + instanceId +
                " (" + serviceName + ") at " + url);
    }

    /** Starts periodic health monitoring. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAll,
                checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        System.out.println("[ClusterManager] Started (check every " +
                checkIntervalMs + "ms, failure after " + failureThresholdMs + "ms)");
    }

    /** Stops monitoring. */
    public void stop() {
        scheduler.shutdownNow();
        System.out.println("[ClusterManager] Stopped");
    }

    /** Returns the current status of all managed instances. */
    public Map<String, Map<String, Object>> getStatus() {
        Map<String, Map<String, Object>> status = new LinkedHashMap<>();
        for (ManagedInstance inst : instances.values()) {
            status.put(inst.instanceId, Map.of(
                    "service", inst.serviceName,
                    "url", inst.url,
                    "status", inst.status.name(),
                    "lastHealthy", inst.lastHealthyTime
            ));
        }
        return status;
    }

    // =====================================================================
    // Health check loop
    // =====================================================================

    private void checkAll() {
        for (ManagedInstance inst : instances.values()) {
            checkInstance(inst);
        }
    }

    private void checkInstance(ManagedInstance inst) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(inst.url + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                if (inst.status != InstanceStatus.HEALTHY) {
                    System.out.println("[ClusterManager] " + inst.instanceId +
                            " recovered → HEALTHY");
                }
                inst.status = InstanceStatus.HEALTHY;
                inst.lastHealthyTime = System.currentTimeMillis();
            } else {
                handleUnhealthy(inst);
            }
        } catch (Exception e) {
            handleUnhealthy(inst);
        }
    }

    private void handleUnhealthy(ManagedInstance inst) {
        long downTime = System.currentTimeMillis() - inst.lastHealthyTime;

        if (inst.status == InstanceStatus.HEALTHY) {
            inst.status = InstanceStatus.SUSPECTED;
            System.out.println("[ClusterManager] " + inst.instanceId +
                    " → SUSPECTED (no health response)");
        } else if (inst.status == InstanceStatus.SUSPECTED &&
                downTime >= failureThresholdMs) {
            inst.status = InstanceStatus.FAILED;
            System.out.println("[ClusterManager] " + inst.instanceId +
                    " → FAILED (down for " + (downTime / 1000) + "s)");

            try {
                failureCallback.onInstanceFailed(
                        inst.serviceName, inst.instanceId, inst.url);
            } catch (Exception e) {
                System.err.println("[ClusterManager] Failure callback error for " +
                        inst.instanceId + ": " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // Internal types
    // =====================================================================

    private enum InstanceStatus {
        HEALTHY,
        SUSPECTED,
        FAILED
    }

    private static class ManagedInstance {
        final String serviceName;
        final String instanceId;
        final String url;
        volatile InstanceStatus status;
        volatile long lastHealthyTime;

        ManagedInstance(String serviceName, String instanceId, String url,
                       InstanceStatus status, long lastHealthyTime) {
            this.serviceName = serviceName;
            this.instanceId = instanceId;
            this.url = url;
            this.status = status;
            this.lastHealthyTime = lastHealthyTime;
        }
    }
}
