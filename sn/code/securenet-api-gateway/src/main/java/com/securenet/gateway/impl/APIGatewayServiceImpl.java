package com.securenet.gateway.impl;

import com.securenet.common.LoadBalancer;
import com.securenet.common.ServiceClient;
import com.securenet.common.ServiceClient.ServiceResponse;
import com.securenet.gateway.APIGatewayService;
import com.securenet.model.AuthToken;
import com.securenet.model.exception.AuthenticationException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Implementation of the API Gateway.
 *
 * <p>Single entry point for all client HTTPS/JSON requests. Authenticates
 * every request via the User Management Service, then routes to the
 * appropriate downstream service using a {@link LoadBalancer} per service.
 *
 * <p>Implements DS Problem #3 (Load Balancing) — each downstream service
 * has a LoadBalancer that distributes requests round-robin across healthy
 * instances and automatically removes unhealthy ones.
 */
public class APIGatewayServiceImpl implements APIGatewayService {

    private static final Logger log = Logger.getLogger(APIGatewayServiceImpl.class.getName());

    private final ServiceClient httpClient;
    private final LoadBalancer umsLoadBalancer;
    private final Map<String, LoadBalancer> serviceLoadBalancers;

    /**
     * @param umsLoadBalancer         load balancer for UMS (token validation)
     * @param serviceLoadBalancers    map of service name → LoadBalancer for
     *                                downstream routing (e.g. "device-management",
     *                                "event-processing", "video-streaming",
     *                                "notification")
     */
    public APIGatewayServiceImpl(LoadBalancer umsLoadBalancer,
                                 Map<String, LoadBalancer> serviceLoadBalancers) {
        this.httpClient = new ServiceClient();
        this.umsLoadBalancer = Objects.requireNonNull(umsLoadBalancer);
        this.serviceLoadBalancers = new ConcurrentHashMap<>(serviceLoadBalancers);
        log.info("[APIGateway] Initialized with services: " + serviceLoadBalancers.keySet());
    }

    @Override
    public AuthToken authenticateRequest(String rawBearerToken) throws AuthenticationException {
        if (rawBearerToken == null || rawBearerToken.isBlank()) {
            log.warning("[APIGateway] AUTH REJECTED: missing bearer token");
            throw new AuthenticationException("Missing bearer token");
        }

        String umsUrl;
        try {
            umsUrl = umsLoadBalancer.nextHealthyUrl();
        } catch (RuntimeException e) {
            log.warning("[APIGateway] AUTH FAILED: no healthy UMS instances");
            throw new AuthenticationException("UMS unreachable: no healthy instances");
        }

        log.info("[APIGateway] Validating token via UMS at " + umsUrl);
        try {
            ServiceResponse resp = httpClient.post(
                    umsUrl + "/ums/validate-token",
                    Map.of("token", rawBearerToken)
            );

            if (resp.statusCode() == 401) {
                log.warning("[APIGateway] AUTH REJECTED: invalid or expired token (UMS returned 401)");
                throw new AuthenticationException("Invalid or expired token");
            }
            if (!resp.isSuccess()) {
                log.warning("[APIGateway] AUTH FAILED: UMS returned status=" + resp.statusCode());
                throw new AuthenticationException("Token validation failed");
            }

            AuthToken token = resp.bodyAs(AuthToken.class);
            log.info("[APIGateway] AUTH OK: userId=" + token.userId());
            return token;
        } catch (IOException e) {
            log.warning("[APIGateway] AUTH FAILED: UMS unreachable at " + umsUrl + " — " + e.getMessage());
            throw new AuthenticationException("UMS unreachable: " + e.getMessage());
        }
    }

    @Override
    public String routeRequest(AuthToken token, String serviceName,
                               String endpoint, String payload)
            throws AuthenticationException, ServiceUnavailableException {

        LoadBalancer lb = serviceLoadBalancers.get(serviceName);
        if (lb == null) {
            log.warning("[APIGateway] ROUTE FAILED: unknown service '" + serviceName + "'");
            throw new ServiceUnavailableException(serviceName);
        }

        String targetUrl;
        try {
            targetUrl = lb.nextHealthyUrl();
        } catch (RuntimeException e) {
            logServiceIncident(serviceName, "all", "No healthy instances");
            throw new ServiceUnavailableException(serviceName);
        }

        String fullUrl = targetUrl + endpoint;
        String method = (payload != null && !payload.isBlank()) ? "POST" : "GET";
        log.info("[APIGateway] ROUTING: userId=" + token.userId()
                + " " + method + " " + serviceName + " -> " + fullUrl);

        try {
            String responseBody;
            if (payload != null && !payload.isBlank()) {
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(fullUrl))
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                        .header("Content-Type", "application/json")
                        .timeout(java.time.Duration.ofSeconds(10))
                        .build();
                var response = java.net.http.HttpClient.newHttpClient()
                        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                responseBody = response.body();
                log.info("[APIGateway] RESPONSE: " + serviceName + " status="
                        + response.statusCode() + " bodyLen=" + responseBody.length());
            } else {
                ServiceResponse resp = httpClient.get(fullUrl);
                responseBody = resp.body();
                log.info("[APIGateway] RESPONSE: " + serviceName + " status="
                        + resp.statusCode() + " bodyLen=" + responseBody.length());
            }
            return responseBody;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logServiceIncident(serviceName, targetUrl, "interrupted");
            throw new ServiceUnavailableException(serviceName);
        } catch (IOException e) {
            logServiceIncident(serviceName, targetUrl, e.getMessage());
            throw new ServiceUnavailableException(serviceName);
        }
    }

    @Override
    public void logServiceIncident(String serviceName, String instanceId, String reason) {
        log.warning("[APIGateway] SERVICE INCIDENT: service=" + serviceName
                + " instance=" + instanceId + " reason=" + reason);
    }
}