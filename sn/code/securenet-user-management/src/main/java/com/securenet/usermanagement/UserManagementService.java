package com.securenet.usermanagement;


import com.securenet.model.AuthToken;
import com.securenet.model.User;
import com.securenet.model.exception.AuthenticationException;

import java.util.Optional;

/**
 * Public API of the SecureNet User Management Service.
 *
 * <p>This service owns all homeowner identity data and is the sole authority
 * for issuing and revoking AuthTokens. It stores user profiles in the
 * { com.securenet.storage.StorageService} (PostgreSQL) and reads/writes
 * user data via SQL/JDBC as shown in the C4 Container Diagram.
 *
 * <p>It's responsibilities are:</p>
 *     - Account registration and profile management.
 * <p> - Password-based authentication and token issuance.
 * <p> - Token validation — called by the API Gateway on every inbound request via HTTPS/REST.
 * <p> - Token revocation on logout.
 *
 * <p>Callers:</p>
 * { com.securenet.gateway.APIGatewayService} — validates every inbound token before routing.
 *
 * <p>Protocol:</p>
 * HTTPS/REST on the internal service mesh.
 */
public interface UserManagementService {

    /**
     * Registers a new homeowner account.
     *
     * <p>The email address must be unique across the platform. The raw password
     * is hashed before storage; it is never persisted in plaintext.
     *
     * @param email          verified email address; used as the login identifier
     * @param displayName    human-readable name shown in the application UI
     * @param password     password supplied by the user during sign-up
     * @return the newly created User profile (without any credential data)
     * @throws IllegalArgumentException if the email is already registered or the
     *                                  password fails the complexity policy
     */
    User registerUser(String email, String displayName, String password)
            throws IllegalArgumentException;

    /**
     * Authenticates a homeowner with their email and password and, on success,
     * issues a short-lived bearer AuthToken.
     *
     * <p>The returned token must be presented in the {@code Authorization} header
     * of every subsequent API call.
     *
     * @param email       the registered email address
     * @param password the password to verify
     * @return a valid AuthToken scoped to the authenticated user
     * @throws AuthenticationException if the credentials are invalid or the
     *                                 account has been suspended
     */
    AuthToken login(String email, String password)
            throws AuthenticationException;

    /**
     * Validates a bearer token presented by the API Gateway and returns the
     * associated AuthToken if it is still valid.
     *
     * <p>This is a hot path — it is called on every inbound HTTPS request.
     * Implementations should validate the token's signature and expiry without
     * performing a database round-trip where possible (e.g. using a signed JWT).
     *
     * @param rawBearerToken the raw token string from the HTTP header
     * @return the validated AuthToken
     * @throws AuthenticationException if the token is missing, malformed,
     *                                 expired, or has been explicitly revoked
     */
    AuthToken validateToken(String rawBearerToken)
            throws AuthenticationException;

    /**
     * Revokes the given token, immediately invalidating it for all future
     * requests.
     *
     * <p>Called on logout or when a password change is requested.
     *
     * @param token the token to revoke
     */
    void revokeToken(AuthToken token);

    /**
     * Retrieves the profile of the given user.
     *
     * @param userId the platform-assigned user identifier
     * @return an {@link Optional} containing the User if found,
     *         or empty if no such user exists
     */
    Optional<User> getUserById(String userId);

    /**
     * Updates the display name associated with a user account.
     *
     * @param userId         the user whose profile should be updated
     * @param newDisplayName the replacement display name
     * @return the updated User profile
     * @throws IllegalArgumentException if the user does not exist
     */
    User updateDisplayName(String userId, String newDisplayName)
            throws IllegalArgumentException;

    /**
     * Deactivates a user account and revokes all of its tokens.
     *
     * <p>Deactivated accounts cannot log in but their data is retained for
     * audit purposes.
     *
     * @param userId the user to deactivate
     * @throws IllegalArgumentException if the user does not exist
     */
    void deactivateUser(String userId) throws IllegalArgumentException;
}
