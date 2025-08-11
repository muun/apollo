package io.muun.apollo.data.afs

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.muun.apollo.data.os.OS
import io.muun.apollo.domain.model.InstallSourceInfo
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes

class PackageManagerInfoProvider(private val context: Context) {

    val appInfo: PackageManagerAppInfo
        get() {
            val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            return PackageManagerAppInfo(
                applicationInfo.name ?: "",
                applicationInfo.loadLabel(context.packageManager).toString(),
                applicationInfo.icon,
                (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                (applicationInfo.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
            )
        }

    val deviceFeatures: PackageManagerDeviceFeatures
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

            return PackageManagerDeviceFeatures(
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
                val lastSignature = if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners.lastOrNull()
                } else {
                    signingInfo?.signingCertificateHistory?.lastOrNull()
                }
                return if (lastSignature != null) {
                    Encodings.bytesToHex(Hashes.sha256(lastSignature.toByteArray()))
                } else {
                    Constants.EMPTY
                }
            }
            return Constants.UNKNOWN
        }

    val firstInstallTimeInMs: Long
        get() {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return packageInfo.firstInstallTime
        }

    val installSourceInfo: InstallSourceInfo
        get() {
            if (OS.supportsInstallSourceInfo()) {
                val installSourceInfo =
                    context.packageManager.getInstallSourceInfo(context.packageName)
                // Not using originatingPackageName since we don't have INSTALL_PACKAGES permission
                // See: https://developer.android.com/reference/android/content/pm/PackageManager#getInstallSourceInfo(java.lang.String)
                val installingPackageName = installSourceInfo.installingPackageName
                val initiatingPackageSigningInfo =
                    installSourceInfo.initiatingPackageSigningInfo.toString()
                val initiatingPackageName = installSourceInfo.initiatingPackageName

                return InstallSourceInfo(
                    installingPackageName.toString(),
                    initiatingPackageName,
                    initiatingPackageSigningInfo
                )
            } else {
                return InstallSourceInfo(
                    context.packageManager.getInstallerPackageName(context.packageName).toString()
                )
            }
        }
}