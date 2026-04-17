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
    }

    @Override
    public AuthToken authenticateRequest(String rawBearerToken) throws AuthenticationException {
        if (rawBearerToken == null || rawBearerToken.isBlank()) {
            throw new AuthenticationException("Missing bearer token");
        }
        try {
            String umsUrl = umsLoadBalancer.nextHealthyUrl();
            ServiceResponse resp = httpClient.post(
                    umsUrl + "/ums/validate-token",
                    Map.of("token", rawBearerToken)
            );

            if (resp.statusCode() == 401) {
                throw new AuthenticationException("Invalid or expired token");
            }
            if (!resp.isSuccess()) {
                throw new AuthenticationException("Token validation failed");
            }

            return resp.bodyAs(AuthToken.class);
        } catch (IOException e) {
            throw new AuthenticationException("UMS unreachable: " + e.getMessage());
        }
    }

    @Override
    public String routeRequest(AuthToken token, String serviceName,
                                String endpoint, String payload)
            throws AuthenticationException, ServiceUnavailableException {

        LoadBalancer lb = serviceLoadBalancers.get(serviceName);
        if (lb == null) {
            throw new ServiceUnavailableException(serviceName);
        }

        String targetUrl;
        try {
            targetUrl = lb.nextHealthyUrl();
        } catch (RuntimeException e) {
            logServiceIncident(serviceName, "all", "No healthy instances");
            throw new ServiceUnavailableException(serviceName);
        }

        try {
            ServiceResponse resp;
            if (payload != null) {
                resp = httpClient.post(targetUrl + endpoint, payload);
            } else {
                resp = httpClient.get(targetUrl + endpoint);
            }
            return resp.body();
        } catch (IOException e) {
            logServiceIncident(serviceName, targetUrl, e.getMessage());
            throw new ServiceUnavailableException(serviceName);
        }
    }

    @Override
    public void logServiceIncident(String serviceName, String instanceId, String reason) {
        System.out.println("[APIGateway] SERVICE INCIDENT: " + serviceName +
                " instance=" + instanceId + " reason=" + reason);
    }
}
