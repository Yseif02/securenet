package com.securenet.demo;

import com.securenet.common.JsonUtil;
import com.securenet.common.ServiceClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StressLoadE2E {

    @Test
    void stressDemo_emitsMachineReadableSummary() throws Exception {
        assertTrue(isHealthy("http://localhost:8443"), "API gateway is not reachable");

        Path summaryPath = Path.of("logs", "latest", "stress-load-summary.json")
                .toAbsolutePath().normalize();
        Files.deleteIfExists(summaryPath);

        Process process = new ProcessBuilder(
                javaBin(),
                "-cp", System.getProperty("java.class.path"),
                "com.securenet.demo.StressDemo",
                "--users", "2",
                "--think-time", "500",
                "--duration-ms", "15000",
                "--summary-file", summaryPath.toString()
        ).redirectErrorStream(true).start();

        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "StressDemo did not exit in time");
        process.getInputStream().readAllBytes();
        assertEquals(0, process.exitValue(), "StressDemo exited with non-zero status");
        assertTrue(Files.exists(summaryPath), "StressDemo did not write summary file");

        Map<String, Object> summary = JsonUtil.fromJson(Files.readString(summaryPath), Map.class);
        long totalRequests = ((Number) summary.get("totalRequests")).longValue();
        long usersReady = ((Number) summary.get("usersReady")).longValue();
        double errorRate = ((Number) summary.get("errorRatePct")).doubleValue();

        assertTrue(totalRequests > 0, "Expected stress demo to execute requests");
        assertTrue(usersReady > 0, "Expected at least one user to complete onboarding");
        assertTrue(errorRate < 75.0, "Expected stress error rate to stay below 75%, got " + errorRate);
    }

    private static boolean isHealthy(String baseUrl) {
        try {
            return new ServiceClient().get(baseUrl + "/health").isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
