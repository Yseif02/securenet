package com.securenet.demo;

import com.securenet.client.impl.ClientApplicationServiceImpl;
import com.securenet.common.JsonUtil;
import com.securenet.model.Device;
import com.securenet.model.DeviceType;
import com.securenet.model.EventSummary;
import com.securenet.model.exception.DeviceOfflineException;
import com.securenet.storage.StorageGateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stress Demo — 10 concurrent simulated users each with real mock devices,
 * running continuous operations through the full platform stack.
 *
 * <p>Designed to run alongside {@link FailoverDemo} to verify that fault
 * tolerance works correctly under load. Each user has their own registered
 * account and 3 devices (sensor, camera, lock) that communicate via MQTT.
 *
 * <h3>What each user thread does (continuously)</h3>
 * <ol>
 *   <li>Lock + Unlock the smart lock (exercises DMS → IDFS → MQTT → device ack)</li>
 *   <li>Query event timeline (exercises Gateway → EPS)</li>
 *   <li>Load dashboard (exercises Gateway → DMS)</li>
 *   <li>Start a live stream (exercises DMS → VSS → MQTT → camera chunks)</li>
 *   <li>Wait, then repeat</li>
 * </ol>
 *
 * <p>Mock devices run in their own threads and continuously publish MQTT
 * events. This generates EPS → Notification traffic independently.
 *
 * <h3>Stats reporter</h3>
 * <p>Prints a summary every 10 seconds showing requests/sec, success rate,
 * and per-operation error counts across all threads.
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>Platform running: {@code ./scripts/start-platform.sh}</li>
 *   <li>Firmware already seeded (run {@link PlatformDemo} first, or this
 *       demo seeds it automatically)</li>
 * </ul>
 *
 * <h3>Run</h3>
 * <pre>
 * java -cp "..." com.securenet.demo.StressDemo [--users 10] [--think-time 2000]
 * </pre>
 */
public class StressDemo {

    private static final String GATEWAY = "http://localhost:8443";
    private static final String UMS     = "http://localhost:9001";
    private static final String STORAGE = "http://localhost:9000,http://localhost:9010,http://localhost:9020";
    private static final String IDFS    = "http://localhost:8080";

    private static final String PASSWORD = "stress123";
    private static final int    STATS_INTERVAL_MS = 10_000;

    // ── Global counters (all threads write to these) ───────────────────────
    static final AtomicLong   totalRequests  = new AtomicLong(0);
    static final AtomicLong   totalErrors    = new AtomicLong(0);
    static final AtomicLong   totalLatencyMs = new AtomicLong(0);

    // Per-operation error counters
    static final AtomicInteger errLock        = new AtomicInteger(0);
    static final AtomicInteger errUnlock      = new AtomicInteger(0);
    static final AtomicInteger errDashboard   = new AtomicInteger(0);
    static final AtomicInteger errEventQuery  = new AtomicInteger(0);
    static final AtomicInteger errStream      = new AtomicInteger(0);
    static final AtomicInteger errOnboarding  = new AtomicInteger(0);

    // How many users have fully onboarded (devices ONLINE)
    static final AtomicInteger usersReady     = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        int numUsers   = 10;
        int thinkTimeMs = 2000; // pause between operation loops per user
        long durationMs = 0;
        String summaryFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--users"      -> numUsers    = Integer.parseInt(args[++i]);
                case "--think-time" -> thinkTimeMs = Integer.parseInt(args[++i]);
                case "--duration-ms" -> durationMs = Long.parseLong(args[++i]);
                case "--summary-file" -> summaryFile = args[++i];
            }
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            SecureNet — Concurrent Stress Demo                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  Users:       " + numUsers);
        System.out.println("  Think time:  " + thinkTimeMs + "ms between operation loops");
        System.out.println("  Gateway:     " + GATEWAY);
        System.out.println();

        // Seed firmware once before any users start
        seedFirmware();

        // Start user threads
        List<UserThread> users = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(numUsers + 2);

        for (int i = 1; i <= numUsers; i++) {
            UserThread u = new UserThread(i, thinkTimeMs);
            users.add(u);
            pool.submit(u);
            Thread.sleep(500); // stagger startups so MQTT connections don't burst
        }

        // Start stats reporter
        pool.submit(new StatsReporter(numUsers));

        System.out.println("\n[StressDemo] All " + numUsers + " user threads launched.");
        System.out.println("[StressDemo] Waiting for users to onboard...");
        System.out.println("[StressDemo] Press Ctrl+C to stop.\n");

        // Shutdown hook — stop all devices cleanly
        long startedAtMs = System.currentTimeMillis();
        AtomicBoolean shuttingDown = new AtomicBoolean(false);
        String finalSummaryFile = summaryFile;
        int finalNumUsers = numUsers;
        Runnable shutdown = () -> {
            if (!shuttingDown.compareAndSet(false, true)) return;
            System.out.println("\n[StressDemo] Shutting down...");
            users.forEach(UserThread::shutdown);
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            printFinalSummary();
            writeSummaryFile(finalSummaryFile, finalNumUsers, startedAtMs);
        };

        Runtime.getRuntime().addShutdownHook(new Thread(shutdown));

        if (durationMs > 0) {
            Thread.sleep(durationMs);
            shutdown.run();
            return;
        }

        // Run forever — Ctrl+C triggers shutdown hook
        Thread.currentThread().join();
    }

    // =========================================================================
    // Firmware seeding
    // =========================================================================

    private static void seedFirmware() {
        System.out.println("[StressDemo] Seeding firmware...");
        StorageGateway storage = new StorageGateway(STORAGE);
        for (DeviceType dt : DeviceType.values()) {
            try {
                storage.saveFirmwareBinary(dt.name(), "1.0.0", "SECURENET_FW".getBytes());
                System.out.println("  " + dt.name() + " v1.0.0 seeded");
            } catch (Exception e) {
                System.out.println("  " + dt.name() + " already seeded");
            }
        }
        System.out.println();
    }

    // =========================================================================
    // User thread
    // =========================================================================

    static class UserThread implements Runnable {

        private final int userId;
        private final int thinkTimeMs;
        private volatile boolean running = true;

        // Mock devices owned by this user
        private MockSensor sensor;
        private MockCamera camera;
        private MockLock   lock;
        private final List<Thread> deviceThreads = new ArrayList<>();

        // Client API handle
        private ClientApplicationServiceImpl client;

        // Device IDs for this user
        private String sensorId, cameraId, lockId;

        UserThread(int userId, int thinkTimeMs) {
            this.userId     = userId;
            this.thinkTimeMs = thinkTimeMs;
        }

        void shutdown() {
            running = false;
            if (sensor != null) sensor.shutdown();
            if (camera != null) camera.shutdown();
            if (lock   != null) lock.shutdown();
        }

        @Override
        public void run() {
            String tag = "[User-" + userId + "]";
            try {
                setup(tag);
                operationLoop(tag);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.out.println(tag + " fatal error: " + e.getMessage());
            } finally {
                shutdown();
                System.out.println(tag + " stopped");
            }
        }

        // ── Setup: register, login, onboard devices ───────────────────────

        private void setup(String tag) throws Exception {
            com.securenet.common.ServiceClient rawClient = new com.securenet.common.ServiceClient();

            // Generate email ONCE — reused on every retry so the idempotent
            // registration path in UMS can recognise it as a safe retry of a
            // write whose response was lost (e.g. storage was mid-failover).
            String email = "stress-user-" + userId + "-" + System.currentTimeMillis() + "@stress.test";

            com.securenet.common.ServiceClient.ServiceResponse regResp = null;
            // Allow up to 10 attempts with 5s gaps — a storage failover takes
            // ~20-30s (15s threshold + restart time), so this covers the window.
            int maxAttempts = 10;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                regResp = rawClient.post(UMS + "/ums/register",
                        Map.of("email", email, "displayName", "Stress User " + userId,
                                "password", PASSWORD));
                if (regResp.isSuccess()) break;
                System.out.println(tag + " Registration attempt " + attempt + "/" + maxAttempts
                        + " failed: " + regResp.body() + " — retrying in 5s...");
                if (attempt < maxAttempts) Thread.sleep(5000);
            }
            if (!regResp.isSuccess()) {
                errOnboarding.incrementAndGet();
                System.out.println(tag + " Registration failed after " + maxAttempts
                        + " attempts, giving up");
                return;
            }

            // Login
            client = new ClientApplicationServiceImpl(GATEWAY, UMS);
            client.login(email, PASSWORD);
            client.registerPushToken("stress-fcm-" + userId + "-" + System.currentTimeMillis());

            // Device IDs — unique per user per run
            String suffix = "u" + userId + "t" + (System.currentTimeMillis() % 100000);
            sensorId = "sensor-" + suffix;
            cameraId = "camera-" + suffix;
            lockId   = "lock-"   + suffix;

            // Register devices via Client API — retry generously since storage
            // may still be recovering when we reach this step.
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    client.startDeviceOnboarding(sensorId + ":st-" + suffix, "MOTION_SENSOR");
                    client.startDeviceOnboarding(cameraId + ":ct-" + suffix, "CAMERA");
                    client.startDeviceOnboarding(lockId   + ":lt-" + suffix, "SMART_LOCK");
                    break; // all 3 succeeded
                } catch (IllegalArgumentException e) {
                    if (attempt == maxAttempts) {
                        System.out.println(tag + " Onboarding failed after " + maxAttempts
                                + " attempts: " + e.getMessage());
                        errOnboarding.incrementAndGet();
                        return;
                    }
                    System.out.println(tag + " Onboarding attempt " + attempt + "/" + maxAttempts
                            + " failed: " + e.getMessage() + " — retrying in 5s...");
                    Thread.sleep(5000);
                }
            }

            // Launch mock devices — they connect via MQTT and onboard themselves
            sensor = new MockSensor(sensorId, "st-" + suffix, IDFS);
            camera = new MockCamera(cameraId, "ct-" + suffix, IDFS);
            lock   = new MockLock(lockId,     "lt-" + suffix, IDFS);

            for (AbstractMockDevice d : List.of(sensor, camera, lock)) {
                Thread t = new Thread(d, tag + "-" + d.getClass().getSimpleName());
                t.setDaemon(true);
                t.start();
                deviceThreads.add(t);
                Thread.sleep(100);
            }

            // Wait for devices to complete onboarding (bootstrap + provision + MQTT connect)
            System.out.println(tag + " Waiting for devices to onboard (8s)...");
            Thread.sleep(8000);

            // Confirm devices online
            List<Device> devices = client.loadDashboard();
            long onlineCount = devices.stream()
                    .filter(d -> "ONLINE".equals(d.status().name()))
                    .count();
            System.out.println(tag + " Setup complete — " + onlineCount
                    + "/" + devices.size() + " devices online");

            usersReady.incrementAndGet();
        }

        // ── Operation loop ─────────────────────────────────────────────────

        private void operationLoop(String tag) throws InterruptedException {
            if (client == null) {
                System.out.println(tag + " Skipping operation loop — setup failed");
                return;
            }

            int cycle = 0;
            while (running) {
                cycle++;
                String cycleTag = tag + "[cycle-" + cycle + "]";

                // 1. Lock
                op(cycleTag, "LOCK", errLock, () -> {
                    client.sendLockCommand(lockId);
                    return null;
                });

                // 2. Unlock
                op(cycleTag, "UNLOCK", errUnlock, () -> {
                    client.sendUnlockCommand(lockId);
                    return null;
                });

                // 3. Dashboard
                op(cycleTag, "DASHBOARD", errDashboard, () ->
                        client.loadDashboard());

                // 4. Event timeline — query last 5 minutes for the sensor
                op(cycleTag, "EVENTS", errEventQuery, () -> {
                    Instant now = Instant.now();
                    List<EventSummary> events = client.loadEventTimeline(
                            sensorId, now.minus(5, ChronoUnit.MINUTES), now, 20);
                    // Also query camera events
                    client.loadEventTimeline(
                            cameraId, now.minus(5, ChronoUnit.MINUTES), now, 20);
                    return events;
                });

                // 5. Every 3rd cycle start a live stream (camera chunks → VSS → archive)
                if (cycle % 3 == 0) {
                    op(cycleTag, "STREAM", errStream, () -> {
                        client.startLiveStream(cameraId);
                        return null;
                    });
                }

                Thread.sleep(thinkTimeMs);
            }
        }

        // ── Generic operation wrapper: tracks latency + errors ─────────────

        private void op(String tag, String opName, AtomicInteger errCounter,
                        ThrowingSupplier<?> action) {
            long t0 = System.currentTimeMillis();
            try {
                action.get();
                long latency = System.currentTimeMillis() - t0;
                totalRequests.incrementAndGet();
                totalLatencyMs.addAndGet(latency);
                System.out.println(tag + " " + opName + " OK (" + latency + "ms)");
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - t0;
                totalRequests.incrementAndGet();
                totalErrors.incrementAndGet();
                errCounter.incrementAndGet();
                totalLatencyMs.addAndGet(latency);
                System.out.println(tag + " " + opName + " ERROR (" + latency + "ms): "
                        + e.getMessage());
            }
        }
    }

    // =========================================================================
    // Stats reporter
    // =========================================================================

    static class StatsReporter implements Runnable {

        private final int numUsers;
        private long lastRequests = 0;
        private long lastErrors   = 0;
        private long lastTime     = System.currentTimeMillis();

        StatsReporter(int numUsers) { this.numUsers = numUsers; }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(STATS_INTERVAL_MS);
                    report();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void report() {
            long now        = System.currentTimeMillis();
            long elapsed    = now - lastTime;
            long requests   = totalRequests.get();
            long errors     = totalErrors.get();
            long latency    = totalLatencyMs.get();

            long deltaReq   = requests - lastRequests;
            long deltaErr   = errors   - lastErrors;
            double rps      = deltaReq * 1000.0 / elapsed;
            double errRate  = requests > 0 ? (errors * 100.0 / requests) : 0;
            double avgLatMs = requests > 0 ? (latency * 1.0 / requests)  : 0;

            System.out.println();
            System.out.println("┌─────────────────────────────────────────────────────────┐");
            System.out.printf( "│  STRESS STATS  [%s]%n",
                    Instant.now().toString().substring(11, 19));
            System.out.printf( "│  Users ready:   %d / %d%n", usersReady.get(), numUsers);
            System.out.printf( "│  Requests/sec:  %.1f  (total: %d)%n", rps, requests);
            System.out.printf( "│  Error rate:    %.1f%%  (total errors: %d, last interval: %d)%n",
                    errRate, errors, deltaErr);
            System.out.printf( "│  Avg latency:   %.0fms%n", avgLatMs);
            System.out.println("│  Error breakdown:");
            System.out.printf( "│    LOCK=%-4d  UNLOCK=%-4d  DASHBOARD=%-4d%n",
                    errLock.get(), errUnlock.get(), errDashboard.get());
            System.out.printf( "│    EVENTS=%-4d  STREAM=%-4d  ONBOARD=%-4d%n",
                    errEventQuery.get(), errStream.get(), errOnboarding.get());
            System.out.println("└─────────────────────────────────────────────────────────┘");
            System.out.println();

            lastRequests = requests;
            lastErrors   = errors;
            lastTime     = now;
        }
    }

    // =========================================================================
    // Final summary
    // =========================================================================

    private static void printFinalSummary() {
        long requests = totalRequests.get();
        long errors   = totalErrors.get();
        long latency  = totalLatencyMs.get();
        double errRate  = requests > 0 ? (errors * 100.0 / requests) : 0;
        double avgLatMs = requests > 0 ? (latency * 1.0 / requests)  : 0;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    STRESS DEMO SUMMARY                       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total requests:  %-43d║%n", requests);
        System.out.printf( "║  Total errors:    %-43d║%n", errors);
        System.out.printf( "║  Error rate:      %-42s║%n", String.format("%.2f%%", errRate));
        System.out.printf( "║  Avg latency:     %-42s║%n", String.format("%.0fms", avgLatMs));
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Per-operation errors:                                       ║");
        System.out.printf( "║    LOCK=%-4d  UNLOCK=%-4d  DASHBOARD=%-4d%-17s║%n",
                errLock.get(), errUnlock.get(), errDashboard.get(), "");
        System.out.printf( "║    EVENTS=%-4d  STREAM=%-4d  ONBOARD=%-4d%-15s║%n",
                errEventQuery.get(), errStream.get(), errOnboarding.get(), "");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static void writeSummaryFile(String summaryFile, int numUsers, long startedAtMs) {
        if (summaryFile == null || summaryFile.isBlank()) return;
        Map<String, Object> summary = new LinkedHashMap<>();
        long requests = totalRequests.get();
        long errors = totalErrors.get();
        long latency = totalLatencyMs.get();
        summary.put("startedAtMs", startedAtMs);
        summary.put("finishedAtMs", System.currentTimeMillis());
        summary.put("totalRequests", requests);
        summary.put("totalErrors", errors);
        summary.put("errorRatePct", requests > 0 ? (errors * 100.0 / requests) : 0.0);
        summary.put("avgLatencyMs", requests > 0 ? (latency * 1.0 / requests) : 0.0);
        summary.put("usersReady", usersReady.get());
        summary.put("usersTarget", numUsers);
        summary.put("errorBreakdown", Map.of(
                "lock", errLock.get(),
                "unlock", errUnlock.get(),
                "dashboard", errDashboard.get(),
                "events", errEventQuery.get(),
                "stream", errStream.get(),
                "onboarding", errOnboarding.get()
        ));

        try {
            Path output = Path.of(summaryFile).toAbsolutePath().normalize();
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, JsonUtil.toJson(summary));
            System.out.println("[StressDemo] Wrote summary: " + output);
        } catch (Exception e) {
            System.err.println("[StressDemo] Failed to write summary file: " + e.getMessage());
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
