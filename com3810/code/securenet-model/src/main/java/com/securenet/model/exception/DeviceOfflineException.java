package com.securenet.model.exception;

public class DeviceOfflineException extends Exception {
    public DeviceOfflineException(String deviceId) {
        super("Device is offline or unresponsive: " + deviceId);
    }
}
