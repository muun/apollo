package io.muun.apollo.data.afs

import kotlinx.serialization.Serializable

/**
 * Structured DeviceFeatures data.
 */
@Serializable
data class PackageManagerDeviceFeatures(
    val proximity: Int,
    val accelerometer: Int,
    val gyro: Int,
    val compass: Int,
    val telephony: Int,
    val pc: Int,
    val pip: Int,
)
