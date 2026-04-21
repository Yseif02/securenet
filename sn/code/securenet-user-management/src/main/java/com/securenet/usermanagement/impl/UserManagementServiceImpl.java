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
import java.util.logging.Logger;

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

    private static final Logger log = Logger.getLogger(UserManagementServiceImpl.class.getName());

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

        log.info("[UMS] Registering user: email=" + email + " displayName=" + displayName);

        // Idempotent registration: if the email already exists, check whether
        // the password matches. If it does, this is a safe retry of a request
        // whose write succeeded but whose HTTP response was lost in transit
        // (e.g. due to a network fault or service restart mid-request).
        // Returning the existing user makes registration idempotent so callers
        // can safely retry without getting a spurious "already registered" error.
        Optional<User> existing = storageGateway.findUserByEmail(email);
        if (existing.isPresent()) {
            String storedHash = storageGateway
                    .findPasswordHashByUserId(existing.get().userId())
                    .orElse(null);
            if (storedHash != null && storedHash.equals(hashPassword(password))) {
                log.info("[UMS] Idempotent re-registration: returning existing user "
                        + "userId=" + existing.get().userId() + " email=" + email);
                return existing.get();
            }
            // Different password — this is a genuine duplicate email conflict
            log.warning("[UMS] Registration failed: email already registered: " + email);
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        String userId = UUID.randomUUID().toString();
        User user = new User(userId, email.trim(), displayName.trim(), Instant.now(), true);

        storageGateway.saveUser(user);
        storageGateway.savePasswordHash(userId, hashPassword(password));
        log.info("[UMS] User registered: userId=" + userId + " email=" + email);
        return user;
    }

    @Override
    public AuthToken login(String email, String password) throws AuthenticationException {
        validateEmail(email);
        Objects.requireNonNull(password, "password");

        log.info("[UMS] Login attempt: email=" + email);

        User user = storageGateway.findUserByEmail(email)
                .orElseThrow(() -> {
                    log.warning("[UMS] Login failed: no account for email=" + email);
                    return new AuthenticationException("Invalid credentials");
                });

        String storedHash = storageGateway.findPasswordHashByUserId(user.userId())
                .orElseThrow(() -> {
                    log.warning("[UMS] Login failed: no password hash for userId=" + user.userId());
                    return new AuthenticationException("Invalid credentials");
                });

        if (!storedHash.equals(hashPassword(password))) {
            log.warning("[UMS] Login failed: wrong password for userId=" + user.userId());
            throw new AuthenticationException("Invalid credentials");
        }

        if (!user.active()) {
            log.warning("[UMS] Login failed: account deactivated userId=" + user.userId());
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
        log.info("[UMS] Login OK: userId=" + user.userId()
                + " tokenId=" + token.tokenValue()
                + " expiresAt=" + token.expiresAt());
        return token;
    }

    @Override
    public AuthToken validateToken(String rawBearerToken) throws AuthenticationException {
        if (rawBearerToken == null || rawBearerToken.isBlank()) {
            log.warning("[UMS] Token validation failed: missing token");
            throw new AuthenticationException("Missing bearer token");
        }

        String tokenValue = extractTokenValue(rawBearerToken);
        log.info("[UMS] Validating token: tokenId=" + tokenValue);

        AuthToken token = storageGateway.findAuthToken(tokenValue)
                .orElseThrow(() -> {
                    log.warning("[UMS] Token validation failed: unknown tokenId=" + tokenValue);
                    return new AuthenticationException("Unknown token");
                });

        if (storageGateway.isTokenRevoked(tokenValue)) {
            log.warning("[UMS] Token validation failed: revoked tokenId=" + tokenValue
                    + " userId=" + token.userId());
            throw new AuthenticationException("Token has been revoked");
        }

        if (!token.isValid()) {
            log.warning("[UMS] Token validation failed: expired tokenId=" + tokenValue
                    + " userId=" + token.userId() + " expiredAt=" + token.expiresAt());
            throw new AuthenticationException("Token has expired");
        }

        User user = storageGateway.findUserById(token.userId())
                .orElseThrow(() -> {
                    log.warning("[UMS] Token validation failed: user not found userId=" + token.userId());
                    return new AuthenticationException("User does not exist");
                });

        if (!user.active()) {
            log.warning("[UMS] Token validation failed: account deactivated userId=" + user.userId());
            throw new AuthenticationException("Account is deactivated");
        }

        log.info("[UMS] Token valid: userId=" + token.userId() + " tokenId=" + tokenValue);
        return token;
    }

    @Override
    public void revokeToken(AuthToken token) {
        Objects.requireNonNull(token, "token");
        log.info("[UMS] Revoking token: tokenId=" + token.tokenValue()
                + " userId=" + token.userId());
        storageGateway.revokeAuthToken(token.tokenValue());
        log.info("[UMS] Token revoked: tokenId=" + token.tokenValue());
    }

    @Override
    public Optional<User> getUserById(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        log.info("[UMS] getUserById: userId=" + userId);
        Optional<User> user = storageGateway.findUserById(userId);
        if (user.isEmpty()) {
            log.warning("[UMS] getUserById: not found userId=" + userId);
        }
        return user;
    }

    @Override
    public User updateDisplayName(String userId, String newDisplayName)
            throws IllegalArgumentException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        validateDisplayName(newDisplayName);

        log.info("[UMS] updateDisplayName: userId=" + userId + " newName=" + newDisplayName);

        User existing = storageGateway.findUserById(userId)
                .orElseThrow(() -> {
                    log.warning("[UMS] updateDisplayName failed: user not found userId=" + userId);
                    return new IllegalArgumentException("User does not exist: " + userId);
                });

        User updated = new User(
                existing.userId(), existing.email(), newDisplayName.trim(),
                existing.createdAt(), existing.active()
        );

        storageGateway.updateUser(updated);
        log.info("[UMS] Display name updated: userId=" + userId);
        return updated;
    }

    @Override
    public void deactivateUser(String userId) throws IllegalArgumentException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        log.info("[UMS] Deactivating user: userId=" + userId);

        User existing = storageGateway.findUserById(userId)
                .orElseThrow(() -> {
                    log.warning("[UMS] deactivateUser failed: user not found userId=" + userId);
                    return new IllegalArgumentException("User does not exist: " + userId);
                });

        User updated = new User(
                existing.userId(), existing.email(), existing.displayName(),
                existing.createdAt(), false
        );

        storageGateway.updateUser(updated);
        log.info("[UMS] User deactivated: userId=" + userId);
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