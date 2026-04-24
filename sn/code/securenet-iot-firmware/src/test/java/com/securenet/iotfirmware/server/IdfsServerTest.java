package com.securenet.iotfirmware.server;

import com.securenet.common.LoadBalancer;
import com.securenet.storage.StorageGateway;
import com.securenet.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdfsServerTest {

    @Test
    void resumeVssSession_returnsRecoveredUrl() throws Exception {
        LoadBalancer dms = mock(LoadBalancer.class);
        LoadBalancer eps = mock(LoadBalancer.class);
        LoadBalancer vss = mock(LoadBalancer.class);
        StorageGateway storageGateway = mock(StorageGateway.class);

        try (TestHttpServer server = TestHttpServer.start()) {
            server.json("/vss/session/resume", 200, Map.of("vssUrl", "http://localhost:9015"));
            when(vss.nextHealthyUrl()).thenReturn(server.baseUrl());

            IdfsServer idfs = new IdfsServer("localhost", 8080, dms, eps, vss,
                    "tcp://localhost:1883", storageGateway);

            Method method = IdfsServer.class.getDeclaredMethod("resumeVssSession", String.class);
            method.setAccessible(true);

            assertEquals("http://localhost:9015", method.invoke(idfs, "rec-1"));
        }
    }

    @Test
    void extractDeviceIdFromTopic_returnsNullForMalformedTopic() throws Exception {
        Method method = IdfsServer.class.getDeclaredMethod("extractDeviceIdFromTopic", String.class);
        method.setAccessible(true);

        assertEquals("camera-1",
                method.invoke(null, "securenet/devices/camera-1/stream/chunks"));
        assertNull(method.invoke(null, "invalid-topic"));
    }
}
