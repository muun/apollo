package io.muun.apollo.presentation.ui.nfc.events

import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

/**
 * Represents the device's magnetic field along the three axes.
 *
 * @param xAxis magnetic field on the x-axis.
 * @param yAxis magnetic field on the y-axis.
 * @param zAxis magnetic field on the z-axis.
 */
internal data class MagneticField(val xAxis: Float, val yAxis: Float, val zAxis: Float)

/**
 * Represents a magnetic field sensor event, including the magnetic field strength
 * in microteslas (µT).
 */
internal data class MagneticEvent(
    override val id: Long,
    override val eventType: String = "magnetic",
    val magneticFieldUt: MagneticField,
) : ISensorEvent {

    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "magnetic_field_ut_x" to magneticFieldUt.xAxis.toString(),
                "magnetic_field_ut_y" to magneticFieldUt.yAxis.toString(),
                "magnetic_field_ut_z" to magneticFieldUt.zAxis.toString(),
            )
        )
    }
}