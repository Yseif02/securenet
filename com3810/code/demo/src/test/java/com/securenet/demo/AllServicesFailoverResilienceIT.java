package com.securenet.demo;

import com.securenet.demo.failover.FailoverResult;
import com.securenet.demo.failover.ServiceFailoverTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AllServicesFailoverResilienceIT {

    private static final String CLUSTER_MANAGERS = "http://localhost:9090,http://localhost:9091,http://localhost:9092";

    @Test
    void allServicesRecoverThroughLoadBalancerFailover() throws Exception {
        assertTrue(checkClusterManager(CLUSTER_MANAGERS),
                "ClusterManager cluster is not reachable at " + CLUSTER_MANAGERS);

        List<FailoverResult> results = List.of(
                new ServiceFailoverTest("Storage",
                        List.of("http://localhost:9000", "http://localhost:9010", "http://localhost:9020"),
                        CLUSTER_MANAGERS, false).run(),
                new ServiceFailoverTest("UMS",
                        List.of("http://localhost:9001", "http://localhost:9011", "http://localhost:9021"),
                        CLUSTER_MANAGERS, false).run(),
                new ServiceFailoverTest("DMS",
                        List.of("http://localhost:9002", "http://localhost:9012", "http://localhost:9022"),
                        CLUSTER_MANAGERS, false).run(),
                new ServiceFailoverTest("IDFS",
                        List.of("http://localhost:8080", "http://localhost:8081", "http://localhost:8082"),
                        CLUSTER_MANAGERS, false).run(),
                new ServiceFailoverTest("EPS",
                        List.of("http://localhost:9003", "http://localhost:9103", "http://localhost:9203"),
                        CLUSTER_MANAGERS, true).run(),
                new ServiceFailoverTest("Notification",
                        List.of("http://localhost:9004", "http://localhost:9014", "http://localhost:9024"),
                        CLUSTER_MANAGERS, false).run(),
                new ServiceFailoverTest("VSS",
                        List.of("http://localhost:9005", "http://localhost:9015", "http://localhost:9025"),
                        CLUSTER_MANAGERS, false).run()
        );

        List<String> failures = results.stream()
                .filter(result -> !result.passed())
                .map(result -> result.serviceName() + ": " + result.message())
                .toList();

        assertTrue(failures.isEmpty(), "Failover failures: " + failures);
    }

    private static boolean checkClusterManager(String url) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        for (String candidate : url.split(",")) {
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) continue;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(trimmed + "/health"))
                        .GET()
                        .timeout(Duration.ofSeconds(3))
                        .build();
                if (client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
