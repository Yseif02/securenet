package com.securenet.videostreaming.impl;

import com.securenet.storage.StorageGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.mockito.Mockito.*;

class VideoStreamingServiceImplTest {

    @Test
    void closeRecordingSession_cleansUpStateEvenWhenNoChunksExist() {
        StorageGateway storageGateway = mock(StorageGateway.class);
        when(storageGateway.findRecordingSession("rec-1")).thenReturn(Optional.of(Map.of(
                "deviceId", "device-1",
                "ownerId", "owner-1",
                "startedAt", Instant.now().toString()
        )));
        when(storageGateway.loadCheckpointedChunks("rec-1")).thenReturn(new TreeMap<>());

        VideoStreamingServiceImpl service = new VideoStreamingServiceImpl(storageGateway);

        service.closeRecordingSession("rec-1");

        verify(storageGateway).deleteRecordingSession("rec-1");
        verify(storageGateway).deleteCheckpointedChunks("rec-1");
        verify(storageGateway, never()).saveVideoClip(any(), any());
    }
}
