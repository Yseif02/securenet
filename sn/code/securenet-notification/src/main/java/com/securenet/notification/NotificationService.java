package com.securenet.notification;


import com.securenet.model.SecurityEvent;

import java.util.List;

/**
 * Public API of the SecureNet Notification Service.
 *
 * <p>This service delivers real-time push notifications to homeowners' mobile
 * and web clients whenever a security-relevant event occurs. It is the sole
 * point of integration with external push infrastructure: Apple Push Notification
 * service (APNs) and Google Firebase Cloud Messaging (FCM).
 *
 * <p>It's responsibilities are:</p>
 *  Device token registry — store and manage the push tokens registered by each homeowner's mobile devices, reading/writing
 *  token data via the Data Storage Layer ({@code Reads/writes Device data / firmware [SQL/JDBC]}).
 *  <p>Alert dispatch — format and send push notifications to all of a user's registered devices when alerted by the Event
 *  Processing Service ({@code Send events [HTTPS/REST]}).
 *  <p>Platform routing — route each notification through APNs for iOS devices or FCM for Android/web clients
 *  ({@code Push notifications [HTTPS/REST]}).
 *
 *
 * <p>Callers:</p>
 * {com.securenet.eventprocessing.EventProcessingService} — sends alert-worthy events over HTTPS/REST.
 * <p>{com.securenet.devicemanagement.DeviceManagementService} — sends device-unresponsive alerts directly.
 *
 *
 * <p>Protocol:</p>
 * HTTPS/REST inbound; HTTPS/REST outbound to APNs and FCM.
 */
public interface NotificationService {

    // =========================================================================
    // Push token management
    // =========================================================================

    /**
     * Registers a mobile push token for a homeowner's device.
     *
     * <p>Called by the Client Application on first launch (or whenever the OS
     * issues a new token). Tokens are stored in the Data Storage Layer and
     * associated with the user's account so that alerts reach all their devices.
     *
     * @param userId       the homeowner's platform user identifier
     * @param pushToken    the APNs or FCM device token supplied by the OS
     * @param platform     the push platform; must be {@code "APNS"} or {@code "FCM"}
     * @throws IllegalArgumentException if {@code platform} is not recognised or
     *                                  the token format is invalid
     */
    void registerPushToken(String userId, String pushToken, String platform)
            throws IllegalArgumentException;

    /**
     * Removes a push token, typically called when a user logs out of a specific
     * device or the OS invalidates the token.
     *
     * @param pushToken the token to remove; silently ignored if not found
     */
    void deregisterPushToken(String pushToken);

    /**
     * Returns all active push tokens registered for a homeowner, across all
     * their devices and platforms.
     *
     * @param userId the homeowner's user identifier
     * @return an unmodifiable list of push tokens; empty if the user has no
     *         registered tokens
     */
    List<String> getPushTokensForUser(String userId);

    // =========================================================================
    // Alert dispatch
    // =========================================================================

    /**
     * Sends a push notification to all of a homeowner's registered devices
     * for the given security event.
     *
     * <p>This is the primary method called by the Event Processing Service.
     * The notification title and body are constructed from the event type and
     * metadata (e.g. device display name, event timestamp). The service routes
     * each notification through APNs or FCM based on the registered platform
     * of each target token.
     *
     * @param event the security event that triggered the alert; must already
     *              be persisted and enriched with owner and device context
     */
    void sendEventAlert(SecurityEvent event);


    // =========================================================================
    // Platform dispatch (internal — called by the methods above)
    // =========================================================================

    /**
     * Dispatches a pre-formatted payload to APNs for an iOS device token.
     *
     * <p>Implementations must handle APNs connection pooling, TLS mutual
     * authentication, and token refresh (HTTP/2 APNs provider API).
     *
     * @param apnsToken the APNs device token
     * @param payload   the JSON APNs payload (alert, sound, badge, custom data)
     * @return {@code true} if APNs accepted the notification; {@code false} if
     *         the token was rejected (caller should deregister the token)
     */
    boolean dispatchViaApns(String apnsToken, String payload);

    /**
     * Dispatches a pre-formatted payload to FCM for an Android or web token.
     *
     * <p>Implementations must handle FCM HTTP v1 API authentication using a
     * Google service-account credential.
     *
     * @param fcmToken the FCM registration token
     * @param payload  the JSON FCM message payload
     * @return {@code true} if FCM accepted the message; {@code false} if the
     *         token was invalid or unregistered (caller should deregister)
     */
    boolean dispatchViaFcm(String fcmToken, String payload);
}
