package com.securenet.gateway.impl;

import com.securenet.common.LoadBalancer;
import com.securenet.gateway.APIGatewayService;
import com.securenet.model.AuthToken;
import com.securenet.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class APIGatewayServiceImplTest {

    @Test
    void authenticateRequest_returnsValidatedToken() throws Exception {
        LoadBalancer umsLoadBalancer = mock(LoadBalancer.class);
        try (TestHttpServer umsServer = TestHttpServer.start()) {
            umsServer.json("/ums/validate-token", 200, Map.of(
                    "tokenValue", "token-1",
                    "userId", "user-1",
                    "issuedAt", Instant.now().toString(),
                    "expiresAt", Instant.now().plusSeconds(60).toString()
            ));
            when(umsLoadBalancer.nextHealthyUrl()).thenReturn(umsServer.baseUrl());

            APIGatewayServiceImpl gateway = new APIGatewayServiceImpl(umsLoadBalancer, Map.of());
            AuthToken token = gateway.authenticateRequest("Bearer token-1");

            assertEquals("user-1", token.userId());
        }
    }

    @Test
    void routeRequest_throwsForUnknownService() {
        APIGatewayServiceImpl gateway = new APIGatewayServiceImpl(mock(LoadBalancer.class), Map.of());
        AuthToken token = new AuthToken("token-1", "user-1", Instant.now(), Instant.now().plusSeconds(60));

        assertThrows(APIGatewayService.ServiceUnavailableException.class,
                () -> gateway.routeRequest(token, "missing", "/path", null));
    }
}
