package com.securenet.eventprocessing.impl;

import com.securenet.common.LoadBalancer;
import com.securenet.model.EventType;
import com.securenet.model.SecurityEvent;
import com.securenet.storage.StorageGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EventProcessingServiceImplTest {

    @Test
    void ingestEvent_returnsOriginalEventWhenDedupEntryExists() throws Exception {
        StorageGateway storageGateway = mock(StorageGateway.class);
        LoadBalancer dmsLoadBalancer = mock(LoadBalancer.class);
        Instant occurredAt = Instant.now();
        SecurityEvent original = new SecurityEvent(
                "event-1", "device-1", "owner-1", EventType.MOTION_DETECTED,
                occurredAt, Map.of("nonce", "n1"));

        when(storageGateway.findDeduplicationEntry("device-1:MOTION_DETECTED:n1"))
                .thenReturn(Optional.of(Map.of(
                        "eventId", "event-1",
                        "recordedAt", Instant.now().toString()
                )));
        when(storageGateway.findEventById("event-1")).thenReturn(Optional.of(original));

        EventProcessingServiceImpl service = new EventProcessingServiceImpl(
                storageGateway, null, "node-1", dmsLoadBalancer);

        SecurityEvent result = service.ingestEvent(
                "device-1", EventType.MOTION_DETECTED, occurredAt, Map.of("nonce", "n1"));

        assertEquals(original, result);
        verify(storageGateway, never()).saveEvent(any());
        verify(storageGateway, never()).saveDeduplicationEntry(anyString(), anyString(), any());
    }
}
