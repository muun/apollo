package io.muun.apollo.presentation.ui.nfc.events

import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Represents the acceleration values along the x, y, and z axes, including the total magnitude vector.
 *
 * @param x Acceleration along the x-axis.
 * @param y Acceleration along the y-axis.
 * @param z Acceleration along the z-axis.
 */
internal data class Acceleration(val x: Float, val y: Float, val z: Float)

/**
 * Represents an accelerometer sensor event that includes directional acceleration and its magnitude.
 *
 * Determines if the device is stationary by comparing the acceleration magnitude to [SensorManager.STANDARD_GRAVITY].
 *
 * @param acceleration The [Acceleration] object containing x, y, z components and the total magnitude.
 */
internal data class AccelerometerEvent(val acceleration: Acceleration) : ISensorEvent {
    override fun handle(): List<Pair<String, String>> {
        val magnitude = getMagnitude()

        return listOf(
            "accel_x" to acceleration.x.toString(),
            "accel_y" to acceleration.y.toString(),
            "accel_z" to acceleration.z.toString(),
            "magnitude_vector" to magnitude.toString(),
            "stationary" to isStationary(magnitude).toString()
        )
    }

    /**
     * Checks whether the given acceleration [magnitude] indicates that the device is stationary.
     *
     * Compares the magnitude to [SensorManager.STANDARD_GRAVITY] using a small threshold to account for minor variations.
     *
     * @param magnitude The magnitude of the acceleration vector to evaluate.
     * @return `true` if the magnitude is within the threshold of Earth's gravity, indicating the device is stationary.
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

