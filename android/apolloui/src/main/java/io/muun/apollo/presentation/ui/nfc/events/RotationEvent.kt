package io.muun.apollo.presentation.ui.nfc.events

/**
 * Represents the device's orientation in degrees along the three axes.
 *
 * @param xAxis Rotation around the x-axis (azimuth).
 * @param yAxis Rotation around the y-axis (pitch).
 * @param zAxis Rotation around the z-axis (roll).
 */
internal data class Rotation(val xAxis: Float, val yAxis: Float, val zAxis: Float)

internal data class RotationEvent(val rotation: Rotation) : ISensorEvent {
    override fun handle(): List<Pair<String, String>> {
        return listOf(
            "rotation_x" to rotation.xAxis.toString(),
            "rotation_y" to rotation.yAxis.toString(),
            "rotation_z" to rotation.zAxis.toString()
        )
    }
}