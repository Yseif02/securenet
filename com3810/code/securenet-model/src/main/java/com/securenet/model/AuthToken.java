package com.securenet.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing a short-lived bearer token issued by
 * the UserManagementService after successful authentication.
 *
 * <p>The token is validated by the API Gateway on every inbound HTTPS request
 * before routing to a downstream service.
 */
public record AuthToken(String tokenValue, String userId, Instant issuedAt, Instant expiresAt) {

    /**
     * @param tokenValue opaque signed token string (e.g. a JWT)
     * @param userId     identifier of the authenticated user
     * @param issuedAt   UTC instant of issuance
     * @param expiresAt  UTC instant of expiry
     */
    public AuthToken(String tokenValue, String userId, Instant issuedAt, Instant expiresAt) {
        this.tokenValue = Objects.requireNonNull(tokenValue, "tokenValue");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * @return opaque signed token value
     */
    @Override
    public String tokenValue() {
        return tokenValue;
    }

    /**
     * @return identifier of the authenticated user
     */
    @Override
    public String userId() {
        return userId;
    }

    /**
     * @return UTC instant of issuance
     */
    @Override
    public Instant issuedAt() {
        return issuedAt;
    }

    /**
     * @return UTC instant of expiry
     */
    @Override
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * @return {@code true} if the current time is before the expiry instant
     */
    public boolean isValid() {
        return Instant.now().isBefore(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthToken t)) return false;
        return tokenValue.equals(t.tokenValue);
    }

    @Override
    public int hashCode() {
        return tokenValue.hashCode();
    }
}
