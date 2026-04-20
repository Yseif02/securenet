package com.securenet.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Cluster Manager for SecureNet services.
 *
 * <p>Implements DS Problem #4 (Cluster Manager and Failure Detection).
 * Tracks instance lifecycle: HEALTHY → SUSPECTED → FAILED.
 */
public class ClusterManager {

    private static final Logger log = Logger.getLogger(ClusterManager.class.getName());

    private final Map<String, ManagedInstance> instances = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final long checkIntervalMs;
    private final long failureThresholdMs;
    private final FailureCallback failureCallback;

    @FunctionalInterface
    public interface FailureCallback {
        void onInstanceFailed(String serviceName, String instanceId, String url);
    }

    public ClusterManager(long checkIntervalMs, long failureThresholdMs,
                          FailureCallback failureCallback) {
        this.checkIntervalMs  = checkIntervalMs;
        this.failureThresholdMs = failureThresholdMs;
        this.failureCallback  = Objects.requireNonNull(failureCallback);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "cluster-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public void registerInstance(String serviceName, String instanceId, String url) {
        instances.put(instanceId, new ManagedInstance(
                serviceName, instanceId, url, InstanceStatus.HEALTHY,
                System.currentTimeMillis()));
        log.info("[ClusterManager] Registered " + instanceId
                + " (" + serviceName + ") at " + url);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAll,
                checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        log.info("[ClusterManager] Started (check every " + checkIntervalMs
                + "ms, failure after " + failureThresholdMs + "ms)");
    }

    public void stop() {
        scheduler.shutdownNow();
        System.out.println("[ClusterManager] Stopped");
    }

    public Map<String, Map<String, Object>> getStatus() {
        Map<String, Map<String, Object>> status = new LinkedHashMap<>();
        for (ManagedInstance inst : instances.values()) {
            status.put(inst.instanceId, Map.of(
                    "service",     inst.serviceName,
                    "url",         inst.url,
                    "status",      inst.status.name(),
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
                    log.info("[ClusterManager] " + inst.instanceId
                            + " recovered → HEALTHY");
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
            log.warning("[ClusterManager] " + inst.instanceId
                    + " → SUSPECTED (no health response)");
        } else if (inst.status == InstanceStatus.SUSPECTED
                && downTime >= failureThresholdMs) {
            inst.status = InstanceStatus.FAILED;
            log.warning("[ClusterManager] " + inst.instanceId
                    + " → FAILED (down for " + (downTime / 1000) + "s)");
            try {
                failureCallback.onInstanceFailed(
                        inst.serviceName, inst.instanceId, inst.url);
            } catch (Exception e) {
                log.severe("[ClusterManager] Failure callback error for "
                        + inst.instanceId + ": " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // Internal types
    // =====================================================================

    private enum InstanceStatus { HEALTHY, SUSPECTED, FAILED }

    private static class ManagedInstance {
        final String serviceName;
        final String instanceId;
        final String url;
        volatile InstanceStatus status;
        volatile long lastHealthyTime;

        ManagedInstance(String serviceName, String instanceId, String url,
                        InstanceStatus status, long lastHealthyTime) {
            this.serviceName   = serviceName;
            this.instanceId    = instanceId;
            this.url           = url;
            this.status        = status;
            this.lastHealthyTime = lastHealthyTime;
        }
    }
}