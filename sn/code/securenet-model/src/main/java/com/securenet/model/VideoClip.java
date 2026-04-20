package com.securenet.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object describing a recorded video clip stored in the
 * SecureNet archive (Data Storage Layer).
 *
 * when it archives footage from a camera. Signed streaming URLs are generated
 * on-demand and are intentionally not stored here.
 */
 //* <p>Clips are created by the {@link com.securenet.videostreaming.VideoStreamingService}
public record VideoClip(String clipId, String deviceId, String ownerId, Instant startTime, Duration duration,
                        long fileSizeBytes, String storageKey) {

    /**
     * @param clipId        platform-assigned unique clip identifier
     * @param deviceId      identifier of the camera that recorded this clip
     * @param ownerId       identifier of the homeowner who owns this clip
     * @param startTime     UTC instant at which recording began
     * @param duration      length of the clip
     * @param fileSizeBytes raw size of the stored video file in bytes
     * @param storageKey    opaque key used by the Data Storage Layer to locate
     *                      the file; not a public URL
     */
    public VideoClip(String clipId, String deviceId, String ownerId, Instant startTime, Duration duration, long fileSizeBytes, String storageKey) {
        this.clipId = Objects.requireNonNull(clipId, "clipId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.duration = Objects.requireNonNull(duration, "duration");
        this.fileSizeBytes = fileSizeBytes;
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
    }

    /**
     * @return platform-assigned unique clip identifier
     */
    @Override
    public String clipId() {
        return clipId;
    }

    /**
     * @return identifier of the recording camera
     */
    @Override
    public String deviceId() {
        return deviceId;
    }

    /**
     * @return identifier of the clip's owner
     */
    @Override
    public String ownerId() {
        return ownerId;
    }

    /**
     * @return UTC start time of the recording
     */
    @Override
    public Instant startTime() {
        return startTime;
    }

    /**
     * @return duration of the clip
     */
    @Override
    public Duration duration() {
        return duration;
    }

    /**
     * @return file size in bytes
     */
    @Override
    public long fileSizeBytes() {
        return fileSizeBytes;
    }

    /**
     * @return opaque storage key — pass this to
     * or generate a signed playback URL
     */
     //{@link com.securenet.storage.StorageService} to retrieve raw bytes
    @Override
    public String storageKey() {
        return storageKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VideoClip v)) return false;
        return clipId.equals(v.clipId);
    }

    @Override
    public int hashCode() {
        return clipId.hashCode();
    }

    @Override
    public String toString() {
        return "VideoClip{id=" + clipId + ", device=" + deviceId +
                ", start=" + startTime + ", duration=" + duration + "}";
    }
}
