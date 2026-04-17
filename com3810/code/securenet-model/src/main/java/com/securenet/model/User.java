package com.securenet.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing a SecureNet homeowner account.
 *
 * <p>Passwords and secrets are never stored in this object; only profile
 * information and account metadata are carried here.
 */
public record User(String userId, String email, String displayName, Instant createdAt, boolean active) {

    /**
     * @param userId      platform-assigned unique user identifier
     * @param email       verified email address used for login and alerts
     * @param displayName name shown in the mobile/web application
     * @param createdAt   UTC timestamp of account creation
     * @param active      {@code true} if the account is in good standing
     */
    public User(String userId, String email, String displayName, Instant createdAt, boolean active) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.active = active;
    }

    /**
     * @return platform-assigned unique user identifier
     */
    @Override
    public String userId() {
        return userId;
    }

    /**
     * @return verified email address
     */
    @Override
    public String email() {
        return email;
    }

    /**
     * @return name shown in the application UI
     */
    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * @return UTC timestamp of account creation
     */
    @Override
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * @return {@code true} if the account is active and authorised to use the platform
     */
    @Override
    public boolean active() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return userId.equals(u.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    @Override
    public String toString() {

        return "User{id=" + userId + ", email=" + email + ", active=" + active + "}";

    }
}
