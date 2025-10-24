package io.muun.apollo.presentation.ui.nfc.events

import android.hardware.SensorManager
import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Represents the acceleration values along the x, y, and z axes.
 *
 * @param x Acceleration along the x-axis.
 * @param y Acceleration along the y-axis.
 * @param z Acceleration along the z-axis.
 */
internal data class Acceleration(val x: Float, val y: Float, val z: Float)

/**
 * Represents an accelerometer event, including directional acceleration and computed metrics.
 *
 * Implements [ISensorEvent] to provide sensor event metadata and structured data for persistence.
 *
 * @property id The unique identifier of the event.
 * @property eventType The type of the sensor event, defaulted to "accelerometer".
 * @property acceleration The [Acceleration] data captured at the event.
 * @property timestamp The ISO 8601 formatted timestamp when the event was created.
 */
internal data class AccelerometerEvent(
    override val id: Long,
    override val eventType: String = "accelerometer",
    val acceleration: Acceleration,
) : ISensorEvent {

    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        val magnitude = getMagnitude()

        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "accel_x_ms2" to acceleration.x.toString(),
                "accel_y_ms2" to acceleration.y.toString(),
                "accel_z_ms2" to acceleration.z.toString(),
                "magnitude_vector_ms2" to magnitude.toString(),
                "stationary" to isStationary(magnitude).toString(),
            )
        )
    }

    /**
     * Determines whether the given acceleration [magnitude] suggests the device is stationary.
     *
     * Compares the magnitude to [SensorManager.STANDARD_GRAVITY] using a small threshold to
     * account for minor variations.
     *
     * @param magnitude The magnitude of the acceleration vector to evaluate.
     * @return `true` if the magnitude is within the threshold of Earth's gravity, indicating
     * the device is stationary.
     */
    private fun isStationary(magnitude: Float): Boolean {
        val threshold = 0.1
        val gravity = SensorManager.STANDARD_GRAVITY

        return abs(magnitude - gravity) < threshold
    }

    /**
     * Calculates the magnitude of the acceleration vector using its x, y, and z components.
     *
     * @return The magnitude of the vector computed as the square root of the sum of the squares of its components.
     */
    private fun getMagnitude(): Float {
        val x = acceleration.x
        val y = acceleration.y
        val z = acceleration.z

        return sqrt(x * x + y * y + z * z)
    }
}

