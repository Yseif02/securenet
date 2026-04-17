package com.securenet.model;

/**
 * Categories of Commands that IoT hardware devices can perform.
 */
public enum CommandType {
    /**Engage the smart lock motor */
     LOCK,

    /** Disengage the smart lock motor */
    UNLOCK,

    /**begin pushing a video stream to the supplied target URL */
    STREAM_START,

    /**halt the active video stream */
    STREAM_STOP,

    /**download and apply a firmware update */
    FIRMWARE_UPDATE
}
