package com.securenet.client.impl;

import com.securenet.client.ClientApplicationService;
import com.securenet.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientApplicationServiceImplTest {

    @Test
    void startDeviceOnboarding_rejectsMalformedSuccessfulResponse() throws Exception {
        try (TestHttpServer umsServer = TestHttpServer.start();
             TestHttpServer gatewayServer = TestHttpServer.start()) {
            umsServer.json("/ums/login", 200, Map.of(
                    "tokenValue", "token-1",
                    "userId", "user-1"
            ));
            gatewayServer.json("/api/device-management/dms/devices/register", 200, Map.of("ok", true));

            ClientApplicationServiceImpl client =
                    new ClientApplicationServiceImpl(gatewayServer.baseUrl(), umsServer.baseUrl());
            client.login("user@example.com", "password123");

            assertThrows(IllegalArgumentException.class,
                    () -> client.startDeviceOnboarding("camera-1:token-1", "CAMERA"));
        }
    }

    @Test
    void login_throwsAuthenticationExceptionOnUnauthorized() throws Exception {
        try (TestHttpServer umsServer = TestHttpServer.start()) {
            umsServer.json("/ums/login", 401, Map.of("error", "bad credentials"));

            ClientApplicationServiceImpl client =
                    new ClientApplicationServiceImpl("http://localhost:8443", umsServer.baseUrl());

            assertThrows(ClientApplicationService.AuthenticationException.class,
                    () -> client.login("user@example.com", "wrongpass"));
        }
    }
}
