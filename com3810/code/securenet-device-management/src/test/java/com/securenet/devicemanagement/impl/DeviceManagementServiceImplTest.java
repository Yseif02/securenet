package com.securenet.devicemanagement.impl;

import com.securenet.common.LoadBalancer;
import com.securenet.model.Device;
import com.securenet.model.DeviceStatus;
import com.securenet.model.DeviceType;
import com.securenet.model.exception.DeviceOfflineException;
import com.securenet.storage.StorageGateway;
import com.securenet.testsupport.Eventually;
import com.securenet.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DeviceManagementServiceImplTest {

    @Test
    void sendStreamStartCommand_closesVssSessionWhenStartIsNotAcknowledged() throws Exception {
        StorageGateway storageGateway = mock(StorageGateway.class);
        LoadBalancer idfsLoadBalancer = mock(LoadBalancer.class);
        LoadBalancer vssLoadBalancer = mock(LoadBalancer.class);

        Device device = new Device("camera-1", "Camera", DeviceType.CAMERA, "owner-1",
                DeviceStatus.ONLINE, Instant.now(), "1.0.0");
        when(storageGateway.findDeviceById("camera-1")).thenReturn(Optional.of(device));

        try (TestHttpServer vssServer = TestHttpServer.start();
             TestHttpServer idfsServer = TestHttpServer.start()) {
            AtomicInteger closeCalls = new AtomicInteger();
            vssServer.json("/vss/session/open", 200, Map.of("recordingSessionId", "rec-1"));
            vssServer.handle("/vss/session/close", exchange -> {
                closeCalls.incrementAndGet();
                TestHttpServer.writeJson(exchange, 200, Map.of("closed", true));
            });
            idfsServer.json("/command", 200, Map.of("acknowledged", false));

            when(vssLoadBalancer.nextHealthyUrl()).thenReturn(vssServer.baseUrl());
            when(idfsLoadBalancer.nextHealthyUrl()).thenReturn(idfsServer.baseUrl());

            DeviceManagementServiceImpl service = new DeviceManagementServiceImpl(
                    storageGateway, idfsLoadBalancer, vssLoadBalancer);

            assertThrows(DeviceOfflineException.class,
                    () -> service.sendStreamStartCommand("camera-1", "http://target"));

            Eventually.await("VSS close after failed stream start",
                    Duration.ofSeconds(2),
                    Duration.ofMillis(50),
                    () -> closeCalls.get() == 1);
        }
    }
}
