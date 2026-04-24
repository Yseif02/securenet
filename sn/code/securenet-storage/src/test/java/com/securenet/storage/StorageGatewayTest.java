package com.securenet.storage;

import com.securenet.model.Device;
import com.securenet.model.DeviceStatus;
import com.securenet.model.DeviceType;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StorageGatewayTest {

    @Test
    void findDeviceById_returnsEmptyOn404() throws Exception {
        try (TestHttpServer server = TestHttpServer.start()) {
            server.json("/storage/devices/device-1", 404, java.util.Map.of("error", "missing"));

            StorageGateway gateway = new StorageGateway(server.baseUrl());

            Optional<Device> result = gateway.findDeviceById("device-1");
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void updateDevice_throwsWhenStorageReturns404() throws Exception {
        try (TestHttpServer server = TestHttpServer.start()) {
            server.json("/storage/devices", 404, java.util.Map.of("error", "missing"));

            StorageGateway gateway = new StorageGateway(server.baseUrl());
            Device device = new Device(
                    "device-1",
                    "Camera",
                    DeviceType.CAMERA,
                    "owner-1",
                    DeviceStatus.ONLINE,
                    Instant.now(),
                    "1.0.0"
            );

            assertThrows(DeviceNotFoundException.class, () -> gateway.updateDevice(device));
        }
    }
}
