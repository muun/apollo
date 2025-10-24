package io.muun.apollo.presentation.ui.nfc.events

import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

/**
 * Represents a barometric pressure sensor event, including the barometric
 * pressure in pascals (pa)
 */
internal data class PressureEvent(
    override val id: Long,
    override val eventType: String = "pressure",
    val pressure: Float,
) : ISensorEvent {
    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "barometric_pressure_pa" to pressure.toString()
            )
        )
    }
}