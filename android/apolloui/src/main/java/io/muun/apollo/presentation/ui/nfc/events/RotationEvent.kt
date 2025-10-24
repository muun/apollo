package io.muun.apollo.presentation.ui.nfc.events

import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

/**
 * Represents the device's orientation in degrees along the three axes.
 *
 * @param xAxis Rotation around the x-axis (azimuth).
 * @param yAxis Rotation around the y-axis (pitch).
 * @param zAxis Rotation around the z-axis (roll).
 */
internal data class Rotation(val xAxis: Float, val yAxis: Float, val zAxis: Float)

internal data class RotationEvent(
    override val id: Long,
    override val eventType: String = "rotation",
    val rotation: Rotation,
) : ISensorEvent {

    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "rotation_x_deg" to rotation.xAxis.toString(),
                "rotation_y_deg" to rotation.yAxis.toString(),
                "rotation_z_deg" to rotation.zAxis.toString(),
            )
        )
    }
}