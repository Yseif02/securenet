package com.securenet.gateway;

import com.securenet.model.AuthToken;
import com.securenet.model.exception.AuthenticationException;

/**
 * Public API of the SecureNet API Gateway container.
 *
 * The API Gateway is the single entry-point for all HTTPS/JSON requests
 * originating from Client Applications (mobile and web). Its responsibilities
 * are:
 *
 *   Authentication — validate bearer tokens issued by the User Management Service before forwarding any request.
 *   Routing — dispatch validated requests to the correct downstream service (Device Management, Video Streaming, etc.).
 *   Health-aware load balancing — when a backend service instance is unhealthy, route to a healthy replica and log the incident.
 *
 * Interaction with other containers
 * Calls {com.securenet.usermanagement.UserManagementService#validateToken}
 *       on every inbound request.
 *   Forwards device commands to
 *       { com.securenet.devicemanagement.DeviceManagementService}.
 *   Forwards video requests to
 *       {com.securenet.videostreaming.VideoStreamingService}.
 *   Forwards event requests to
 *       {com.securenet.eventprocessing.EventProcessingService}.
 *
 *
 * Protocol
 * HTTPS/JSON on port 443. Internal forwarding uses HTTPS/REST.
 */
public interface APIGatewayService {

    /**
     * Validates an inbound bearer token and, if valid, returns the authenticated
     * AuthToken}for use in downstream calls.
     *
     * This method is called for every API request before routing.
     *
     * @param rawBearerToken the raw token string extracted from the
     * {@code Authorization: Bearer <token>} = HTTP header
     * @return a validated AuthToken containing the caller's user ID
     *         and expiry information
     * @throws AuthenticationException if the token is missing, malformed,
     *                                 expired, or revoked
     */
    AuthToken authenticateRequest(String rawBearerToken) throws AuthenticationException;

    /**
     * Routes a validated request payload to the appropriate downstream service
     * and returns the raw response body.
     *
     * Routing is determined by the combination of {@code serviceName} and
     * {@code endpoint}. For example:
     *
     * {@code "device-management"} + {@code "/devices/{id}/lock"}
     * {@code "video-streaming"} + {@code "/clips/{id}/stream"}
     *
     *
     * @param token       validated token from {@link #authenticateRequest}
     * @param serviceName logical name of the target backend service
     * @param endpoint    relative API path within that service
     * @param payload     JSON request body; may be {@code null} for GET requests
     * @return JSON response body from the downstream service
     * @throws AuthenticationException  if the token has been revoked between
     *                                  authentication and routing
     * @throws ServiceUnavailableException if no healthy instance of the target
     *                                  service is available
     */
    String routeRequest(AuthToken token, String serviceName, String endpoint, String payload) throws AuthenticationException, ServiceUnavailableException;


    /**
     * Records a service-availability incident in the platform event log.
     *
     * Called automatically by {@link #routeRequest} when load-balancing
     * routes around an unhealthy instance, so that operations teams can
     * investigate degraded replicas.
     *
     * @param serviceName name of the affected service
     * @param instanceId  identifier of the specific unhealthy instance
     * @param reason      human-readable description of the detected failure
     */
    void logServiceIncident(String serviceName, String instanceId, String reason);

    // -------------------------------------------------------------------------
    // Inner exception types scoped to gateway concerns
    // -------------------------------------------------------------------------

    /**
     * Thrown when all known instances of a downstream service are unavailable.
     */
    class ServiceUnavailableException extends Exception {
        public ServiceUnavailableException(String serviceName) {
            super("No healthy instances available for service: " + serviceName);
        }
    }

}
