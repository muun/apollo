package io.muun.apollo.presentation.ui.nfc.events

import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

/**
 * Represents a magnetic field sensor event, including the magnetic field strength
 * in microteslas (µT).
 */
internal data class MagneticEvent(
    override val id: Long,
    override val eventType: String = "magnetic",
    val magnetic: Float,
) : ISensorEvent {

    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "magnetic_field_ut" to magnetic.toString(),
            )
        )
    }
}