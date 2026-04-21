package com.securenet.common;

import java.io.IOException;
import java.net.ServerSocket;
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
 * Tracks instance lifecycle: HEALTHY → SUSPECTED → FAILED, and
 * automatically restarts failed instances via per-service shell scripts.
 *
 * <h3>Auto-restart</h3>
 * <p>When an instance is registered with a {@code restartScript}, the
 * Cluster Manager will:
 * <ol>
 *   <li>Find a free TCP port using {@code ServerSocket(0)}</li>
 *   <li>Execute the restart script with the new port and a generated
 *       instance ID as arguments</li>
 *   <li>Register the replacement instance for monitoring</li>
 *   <li>Mark the original failed instance as REPLACED</li>
 * </ol>
 *
 * <p>EPS nodes are stateful (Raft log) and must restart on their
 * original port. Their restart script handles this case.
 */
public class ClusterManager {

    private static final Logger log = Logger.getLogger(ClusterManager.class.getName());

    private final Map<String, ManagedInstance> instances = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final long checkIntervalMs;
    private final long failureThresholdMs;
    private final String logDir;

    /**
     * @param checkIntervalMs    how often to health-check each instance (ms)
     * @param failureThresholdMs how long SUSPECTED before declaring FAILED (ms)
     * @param logDir             directory where restart script output is appended
     */
    public ClusterManager(long checkIntervalMs, long failureThresholdMs, String logDir) {
        this.checkIntervalMs    = checkIntervalMs;
        this.failureThresholdMs = failureThresholdMs;
        this.logDir             = logDir;
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
     * Register an instance without a restart script (manual recovery only).
     */
    public void registerInstance(String serviceName, String instanceId, String url) {
        registerInstance(serviceName, instanceId, url, null);
    }

    /**
     * Register an instance with a restart script for automatic recovery.
     *
     * @param restartScript absolute path to the shell script that starts a
     *                      replacement, called as:
     *                      {@code script <NEW_PORT> <NEW_INSTANCE_ID>}
     */
    public void registerInstance(String serviceName, String instanceId,
                                 String url, String restartScript) {
        instances.put(instanceId, new ManagedInstance(
                serviceName, instanceId, url, restartScript,
                InstanceStatus.HEALTHY, System.currentTimeMillis()));
        log.info("[ClusterManager] Registered " + instanceId
                + " (" + serviceName + ") at " + url
                + (restartScript != null ? " [auto-restart]" : " [manual]"));
    }

    public void start(long initialDelayMs) {
        scheduler.scheduleAtFixedRate(this::checkAll,
                initialDelayMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        log.info("[ClusterManager] Started (initial delay " + initialDelayMs
                + "ms, check every " + checkIntervalMs
                + "ms, failure after " + failureThresholdMs + "ms)");
    }

    public void stop() {
        scheduler.shutdownNow();
        System.out.println("[ClusterManager] Stopped");
    }

    public Map<String, Map<String, Object>> getStatus() {
        Map<String, Map<String, Object>> status = new LinkedHashMap<>();
        for (ManagedInstance inst : instances.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("service",      inst.serviceName);
            entry.put("url",          inst.url);
            entry.put("status",       inst.status.name());
            entry.put("lastHealthy",  inst.lastHealthyTime);
            entry.put("restartCount", inst.restartCount);
            entry.put("autoRestart",  inst.restartScript != null);
            status.put(inst.instanceId, entry);
        }
        return status;
    }

    // =====================================================================
    // Health check loop
    // =====================================================================

    private void checkAll() {
        for (ManagedInstance inst : instances.values()) {
            if (inst.status == InstanceStatus.REPLACED) continue;
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
                inst.status          = InstanceStatus.HEALTHY;
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

            if (inst.restartScript != null) {
                scheduler.execute(() -> restartInstance(inst));
            } else {
                log.severe("[ClusterManager] " + inst.instanceId
                        + " has no restart script — manual intervention required");
            }
        }
    }

    // =====================================================================
    // Auto-restart
    // =====================================================================

    private void restartInstance(ManagedInstance inst) {
        try {
            int newPort;
            if ("EPS".equals(inst.serviceName)) {
                // Extract original port from URL: "http://localhost:9103" → 9103
                newPort = Integer.parseInt(inst.url.replaceAll(".*:(\\d+)$", "$1"));
            } else {
                newPort = findFreePort();
            }
            String newInstanceId = inst.instanceId + "-r" + (inst.restartCount + 1);
            String newUrl       = "http://localhost:" + newPort;

            log.info("[ClusterManager] Restarting " + inst.serviceName
                    + ": " + inst.instanceId + " → " + newInstanceId
                    + " on port " + newPort);

            ProcessBuilder pb = new ProcessBuilder(
                    inst.restartScript,
                    String.valueOf(newPort),
                    newInstanceId
            );
            // Pass LOG_DIR so the restart script writes to the current run's log dir
            pb.environment().put("LOG_DIR", logDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(
                    new java.io.File(logDir + "/cluster-manager.log")));

            Process process = pb.start();

            // Allow the new process time to bind its port
            Thread.sleep(2000);

            if (!process.isAlive() && process.exitValue() != 0) {
                log.severe("[ClusterManager] Restart script exited with code "
                        + process.exitValue() + " for " + inst.instanceId);
                return;
            }

            // Mark original as replaced, register the replacement
            inst.restartCount++;
            inst.status = InstanceStatus.REPLACED;
            log.info("[ClusterManager] " + inst.instanceId
                    + " → REPLACED by " + newInstanceId);

            registerInstance(inst.serviceName, newInstanceId, newUrl,
                    inst.restartScript);
            log.info("[ClusterManager] Replacement " + newInstanceId
                    + " registered at " + newUrl
                    + " (restart #" + inst.restartCount + " for "
                    + inst.serviceName + ")");

        } catch (Exception e) {
            log.severe("[ClusterManager] Exception during restart of "
                    + inst.instanceId + ": " + e.getMessage());
        }
    }

    /**
     * Finds a free TCP port by binding to port 0.
     * Released immediately — small race window before the new process binds it.
     */
    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    // =====================================================================
    // Internal types
    // =====================================================================

    private enum InstanceStatus { HEALTHY, SUSPECTED, FAILED, REPLACED }

    private static class ManagedInstance {
        final String serviceName;
        final String instanceId;
        final String restartScript;
        volatile String url;
        volatile InstanceStatus status;
        volatile long lastHealthyTime;
        volatile int restartCount;

        ManagedInstance(String serviceName, String instanceId, String url,
                        String restartScript, InstanceStatus status,
                        long lastHealthyTime) {
            this.serviceName     = serviceName;
            this.instanceId      = instanceId;
            this.url             = url;
            this.restartScript   = restartScript;
            this.status          = status;
            this.lastHealthyTime = lastHealthyTime;
            this.restartCount    = 0;
        }
    }
}