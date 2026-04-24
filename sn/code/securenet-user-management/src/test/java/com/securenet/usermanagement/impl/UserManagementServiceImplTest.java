package com.securenet.usermanagement.impl;

import com.securenet.model.AuthToken;
import com.securenet.model.User;
import com.securenet.model.exception.AuthenticationException;
import com.securenet.storage.StorageGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private StorageGateway storageGateway;

    @InjectMocks
    private UserManagementServiceImpl service;

    @Test
    void registerUser_returnsExistingUserForIdempotentRetry() {
        User existing = new User("u1", "user@example.com", "User", Instant.now(), true);
        when(storageGateway.findUserByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(storageGateway.findPasswordHashByUserId("u1"))
                .thenReturn(Optional.of(Integer.toHexString("password123".hashCode())));

        User result = service.registerUser("user@example.com", "User", "password123");

        assertEquals(existing, result);
        verify(storageGateway, never()).saveUser(any());
    }

    @Test
    void validateToken_rejectsRevokedTokens() {
        AuthToken token = new AuthToken("token-1", "u1", Instant.now(), Instant.now().plusSeconds(60));
        when(storageGateway.findAuthToken("token-1")).thenReturn(Optional.of(token));
        when(storageGateway.isTokenRevoked("token-1")).thenReturn(true);

        assertThrows(AuthenticationException.class, () -> service.validateToken("Bearer token-1"));
    }
}
