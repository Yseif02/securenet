package com.securenet.usermanagement.impl;

import com.securenet.model.AuthToken;
import com.securenet.model.User;
import com.securenet.model.exception.AuthenticationException;
import com.securenet.storage.StorageGateway;
import com.securenet.usermanagement.UserManagementService;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Distributed implementation of the User Management Service.
 *
 * <p>Runs as its own process and accesses persistent data through
 * {@link StorageGateway}, which calls the remote Storage Service
 * over HTTP/JSON.
 *
 * <p>Business logic is identical to the previous in-process version;
 * only the data-access transport has changed.
 */
public class UserManagementServiceImpl implements UserManagementService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final long TOKEN_TTL_SECONDS = 3600;

    private final StorageGateway storageGateway;

    /**
     * @param storageGateway HTTP client pointing to the remote Storage Service
     */
    public UserManagementServiceImpl(StorageGateway storageGateway) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
    }

    @Override
    public User registerUser(String email, String displayName, String password)
            throws IllegalArgumentException {
        validateEmail(email);
        validateDisplayName(displayName);
        validatePassword(password);

        if (storageGateway.findUserByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        String userId = UUID.randomUUID().toString();
        User user = new User(userId, email.trim(), displayName.trim(), Instant.now(), true);

        storageGateway.saveUser(user);
        storageGateway.savePasswordHash(userId, hashPassword(password));
        return user;
    }

    @Override
    public AuthToken login(String email, String password) throws AuthenticationException {
        validateEmail(email);
        Objects.requireNonNull(password, "password");

        User user = storageGateway.findUserByEmail(email)
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        String storedHash = storageGateway.findPasswordHashByUserId(user.userId())
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (!storedHash.equals(hashPassword(password))) {
            throw new AuthenticationException("Invalid credentials");
        }

        if (!user.active()) {
            throw new AuthenticationException("Account is deactivated");
        }

        Instant issuedAt = Instant.now();
        AuthToken token = new AuthToken(
                UUID.randomUUID().toString(),
                user.userId(),
                issuedAt,
                issuedAt.plusSeconds(TOKEN_TTL_SECONDS)
        );

        storageGateway.saveAuthToken(token);
        return token;
    }

    @Override
    public AuthToken validateToken(String rawBearerToken) throws AuthenticationException {
        if (rawBearerToken == null || rawBearerToken.isBlank()) {
            throw new AuthenticationException("Missing bearer token");
        }

        String tokenValue = extractTokenValue(rawBearerToken);
        AuthToken token = storageGateway.findAuthToken(tokenValue)
                .orElseThrow(() -> new AuthenticationException("Unknown token"));

        if (storageGateway.isTokenRevoked(tokenValue)) {
            throw new AuthenticationException("Token has been revoked");
        }

        if (!token.isValid()) {
            throw new AuthenticationException("Token has expired");
        }

        User user = storageGateway.findUserById(token.userId())
                .orElseThrow(() -> new AuthenticationException("User does not exist"));

        if (!user.active()) {
            throw new AuthenticationException("Account is deactivated");
        }

        return token;
    }

    @Override
    public void revokeToken(AuthToken token) {
        Objects.requireNonNull(token, "token");
        storageGateway.revokeAuthToken(token.tokenValue());
    }

    @Override
    public Optional<User> getUserById(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        return storageGateway.findUserById(userId);
    }

    @Override
    public User updateDisplayName(String userId, String newDisplayName)
            throws IllegalArgumentException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        validateDisplayName(newDisplayName);

        User existing = storageGateway.findUserById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist: " + userId));

        User updated = new User(
                existing.userId(), existing.email(), newDisplayName.trim(),
                existing.createdAt(), existing.active()
        );

        storageGateway.updateUser(updated);
        return updated;
    }

    @Override
    public void deactivateUser(String userId) throws IllegalArgumentException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        User existing = storageGateway.findUserById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist: " + userId));

        User updated = new User(
                existing.userId(), existing.email(), existing.displayName(),
                existing.createdAt(), false
        );

        storageGateway.updateUser(updated);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void validateEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("A valid email is required");
        }
    }

    private static void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
    }

    private static String extractTokenValue(String rawBearerToken) {
        String trimmed = rawBearerToken.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    /** Placeholder — replace with BCrypt or Argon2 for production. */
    private static String hashPassword(String password) {
        return Integer.toHexString(password.hashCode());
    }
}
