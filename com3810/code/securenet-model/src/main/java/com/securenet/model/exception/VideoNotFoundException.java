package com.securenet.model.exception;

public class VideoNotFoundException extends Exception {
    public VideoNotFoundException(String storageKey) {
        super("Video not found for key: " + storageKey);
    }
}
