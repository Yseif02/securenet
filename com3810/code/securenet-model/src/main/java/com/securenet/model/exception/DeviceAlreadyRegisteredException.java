package com.securenet.model.exception;

public class DeviceAlreadyRegisteredException extends Exception {
    public DeviceAlreadyRegisteredException(String deviceId) {
        super("Device already registered: " + deviceId);
    }
}
