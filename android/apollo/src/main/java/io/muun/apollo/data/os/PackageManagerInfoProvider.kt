package io.muun.apollo.data.os

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.serialization.Serializable
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import javax.inject.Inject

class PackageManagerInfoProvider @Inject constructor(private val context: Context) {

    /**
     * Structured AppInfo data.
     */
    @Serializable
    data class AppInfo(
        val name: String,
        val label: String,
        val icon: Int,
        val debuggable: Boolean,
        val persistent: Boolean,
    )

    /**
     * Structured DeviceFeatures data.
     */
    @Serializable
    data class DeviceFeatures(
        val touch: Int,
        val proximity: Int,
        val accelerometer: Int,
        val gyro: Int,
        val compass: Int,
        val telephony: Int,
        val cdma: Int,
        val gsm: Int,
        val cameras: Int,
        val pc: Int,
        val pip: Int,
        val dactylogram: Int,
    )

    val appInfo: AppInfo
        get() {
            val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            return AppInfo(
                applicationInfo.name ?: "",
                applicationInfo.loadLabel(context.packageManager).toString(),
                applicationInfo.icon,
                (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                (applicationInfo.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
            )
        }


    val deviceFeatures: DeviceFeatures
        get() {
            val packageManager = context.packageManager

            val touchScreen =
                hasFeature(packageManager, PackageManager.FEATURE_TOUCHSCREEN)

            val sensorProximity =
                hasFeature(packageManager, PackageManager.FEATURE_SENSOR_PROXIMITY)

            val sensorAccelerometer =
                hasFeature(packageManager, PackageManager.FEATURE_SENSOR_ACCELEROMETER)

            val sensorGyro =
                hasFeature(packageManager, PackageManager.FEATURE_SENSOR_GYROSCOPE)

            val sensorCompass =
                hasFeature(packageManager, PackageManager.FEATURE_SENSOR_COMPASS)

            val telephony =
                hasFeature(packageManager, PackageManager.FEATURE_TELEPHONY)

            val telephonyCDMA =
                hasFeature(packageManager, PackageManager.FEATURE_TELEPHONY_CDMA)

            val telephonyGSM =
                hasFeature(packageManager, PackageManager.FEATURE_TELEPHONY_GSM)

            val cameraAny =
                hasFeature(packageManager, PackageManager.FEATURE_CAMERA_ANY)

            val pip = if (OS.supportsPIP()) {
                hasFeature(packageManager, PackageManager.FEATURE_PICTURE_IN_PICTURE)
            } else {
                Constants.INT_UNKNOWN
            }

            val pc = if (OS.supportsFeaturePC()) {
                hasFeature(packageManager, PackageManager.FEATURE_PC)
            } else {
                Constants.INT_UNKNOWN
            }

            val dactylogram = if (OS.supportsDactylogram()) {
                hasFeature(packageManager, PackageManager.FEATURE_FINGERPRINT)
            } else {
                Constants.INT_UNKNOWN
            }

            return DeviceFeatures(
                touchScreen,
                sensorProximity,
                sensorAccelerometer,
                sensorGyro,
                sensorCompass,
                telephony,
                telephonyCDMA,
                telephonyGSM,
                cameraAny,
                pc,
                pip,
                dactylogram
            )
        }

    private fun hasFeature(packageManager: PackageManager, feature: String): Int {
        return if (packageManager.hasSystemFeature(feature)) {
            Constants.INT_PRESENT
        } else {
            Constants.INT_ABSENT
        }
    }

    val signatureHash: String
        get() {
            if (OS.supportsGetSigningCerts()) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                val lastSignature = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners.lastOrNull()
                } else {
                    signingInfo.signingCertificateHistory.lastOrNull()
                }
                return if (lastSignature != null) {
                    Encodings.bytesToHex(Hashes.sha256(lastSignature.toByteArray()))
                } else {
                    Constants.EMPTY
                }
            }
            return Constants.UNKNOWN
        }
}