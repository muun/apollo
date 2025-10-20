package io.muun.apollo.presentation.ui.nfc.events

import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

/**
 * Defines the contract for a generic sensor event that provides metadata and a method to convert its data
 * into a [SensorEvent] structure for processing or persistence.
 *
 * Implementations must provide an identifier, event type, and timestamp, and define how the event should be handled.
 */
interface ISensorEvent {

    /**
     * The unique identifier of the sensor event.
     */
    val id: Long

    /**
     * A string describing the type of the sensor event (e.g., "accelerometer", "gesture").
     */
    val eventType: String

    /**
     * The ISO 8601 formatted timestamp when the event occurred.
     */
    val timestamp: ZonedDateTime

    /**
     * Converts the sensor event into a structured SensorEventData object.
     *
     * @return A SensorEventData containing the event's ID, type, timestamp, and associated data.
     */
    fun handle(): SensorEvent
}