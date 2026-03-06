package com.securenet.eventprocessing;



import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Public API of the SecureNet Event Processing Service.
 *
 * <p>This service is responsible for ingesting SecurityEvents emitted
 * by IoT devices, enriching them with platform context, persisting them to the
 * event history in the Data Storage Layer, and forwarding alert-worthy events
 * to the {@link com.securenet.notification.NotificationService}.
 *
 * <p>It's responsibilities are:</p>
 * Event ingestion — receive raw events published by IoT Device Firmware over HTTPS/REST and validate their structure.
 * <p>Enrichment — attach owner ID, device display name, and any relevant clip references before persisting.
 * <p>History maintenance — write enriched events to the Data Storage Layer (SQL/JDBC, {@code Reads/writes Events}) for later
 * retrieval by the Client Application.
 * <p>Alert routing — forward motion-detection and unresponsive-device events to the Notification Service ({@code Send events [HTTPS/REST]}).
 *
 * <p>Callers:</p>
 * IoT Device Firmware — publishes raw security events over HTTPS/REST.
 * {@link com.securenet.gateway.APIGatewayService} — routes homeowner queries for event history.</li>
 *
 * <p>Protocol:</p>
 * HTTPS/REST for inbound events; HTTPS/REST for outbound notification calls.
 */
public interface EventProcessingService {

    // =========================================================================
    // Event ingestion
    // =========================================================================

    /**
     * Ingests a raw security event sent by an IoT device, enriches it with
     * platform context, persists it, and conditionally triggers a push alert.
     *
     * <p>This is the primary entry-point called by IoT Device Firmware immediately
     * after detecting a security-relevant occurrence (motion, lock state change,
     * connectivity change, etc.).
     *
     * <p>Enrichment adds:</p>
     *   Owner ID (looked up from the device registry)
     *   <p>Device display name</p>
     *
     * @param deviceId   the device that detected the event
     * @param type       the category of event that occurred
     * @param occurredAt the UTC instant at which the event was detected on-device;
     *                   must not be in the future
     * @param metadata   event-type-specific key-value pairs (e.g.
     *                   {@code "thumbnailUrl"}, {@code "lockState"}); may be empty
     * @return the fully enriched and persisted SecurityEvent
     * @throws DeviceNotFoundException  if {@code deviceId} is not in the registry
     * @throws IllegalArgumentException if {@code occurredAt} is in the future or
     *                                  required metadata fields are missing for the
     *                                  given event type
     */
    SecurityEvent ingestEvent(String deviceId, EventType type, Instant occurredAt, Map<String, String> metadata) throws DeviceNotFoundException, IllegalArgumentException;

    // =========================================================================
    // Event history queries
    // =========================================================================

    /**
     * Returns the event history for a specific device within a time window,
     * ordered by occurrence time (most recent first).
     *
     * <p>Called by the Client Application (via the API Gateway) when the
     * homeowner navigates to a device's event timeline.
     *
     * @param deviceId  the device whose events to retrieve
     * @param from      inclusive start of the time window (UTC)
     * @param to        inclusive end of the time window (UTC); must not be
     *                  before {@code from}
     * @param maxEvents maximum number of events to return; must be between
     *                  1 and 500 inclusive
     * @return an unmodifiable list of matching SecurityEvents, newest first;
     *         empty if no events exist in the window
     * @throws DeviceNotFoundException  if {@code deviceId} is not in the registry
     * @throws IllegalArgumentException if {@code to} is before {@code from}, or
     *                                  {@code maxEvents} is out of range
     */
    List<SecurityEvent> getEventHistory(String deviceId, Instant from, Instant to, int maxEvents) throws DeviceNotFoundException, IllegalArgumentException;

    /**
     * Returns all events of a given type for a homeowner across all their
     * devices, ordered by occurrence time (most recent first).
     *
     * <p>Used to power the homeowner's global security feed in the mobile app.
     *
     * @param ownerId    the homeowner whose events to retrieve
     * @param type       the event type to filter by
     * @param from       inclusive start of the time window (UTC)
     * @param to         inclusive end of the time window (UTC)
     * @param maxEvents  maximum number of events to return (1–500)
     * @return an unmodifiable list of matching SecurityEvents; empty if none
     */
    List<SecurityEvent> getEventsByTypeForOwner(String ownerId, EventType type, Instant from, Instant to, int maxEvents);

    /**
     * Retrieves a single event by its platform-assigned identifier.
     *
     * @param eventId the event to look up
     * @return the SecurityEvent, or {@code null} if not found
     */
    SecurityEvent getEventById(String eventId);

    // =========================================================================
    // Alert routing
    // =========================================================================

    /**
     * Evaluates whether the given event warrants a push notification and, if so,
     * forwards it to the {@link com.securenet.notification.NotificationService}.
     *
     * <p>Called internally by {@link #ingestEvent} after persistence. Alert
     * criteria include:</p>
     *
     *  MOTION_DETECTED — always alerts.
     *  DEVICE_UNRESPONSIVE — always alerts.
     *  DOOR_UNLOCKED — alerts if the unlock was
     *       not initiated by the owner.
     *
     * @param event the persisted event to evaluate
     */
    void routeAlertIfRequired(SecurityEvent event);
}
