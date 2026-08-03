package io.muun.apollo.data.afs

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.annotation.RequiresApi
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

            return PackageManagerDeviceFeatures(
                sensorProximity,
                sensorAccelerometer,
                sensorGyro,
                sensorCompass,
                telephony,
                pc,
                pip
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
                val lastSignature = getModernSignatures().lastOrNull()
                return if (lastSignature != null) {
                    computeSignatureHash(lastSignature)
                } else {
                    Constants.EMPTY
                }
            }
            return Constants.UNKNOWN
        }

    val allSignatureHashes: List<String>
        get() {
            return try {
                getSignatures()
                    .map(::computeSignatureHash)
                    .distinct()
                    .sorted()
            } catch (_: Exception) {
                listOf(Constants.ERROR)
            }
        }

    val archiveSignatureHashes: List<String>
        get() {
            return try {
                val packageInfo = getArchivePackageInfo() ?: return listOf(Constants.UNKNOWN)

                @Suppress("DEPRECATION")
                packageInfo.signatures?.toList().orEmpty()
                    .map(::computeSignatureHash)
                    .distinct()
                    .sorted()

            } catch (_: Exception) {
                listOf(Constants.ERROR)
            }
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
                val initiatingPackageName = installSourceInfo.initiatingPackageName

                return InstallSourceInfo(
                    installingPackageName.toString(),
                    initiatingPackageName
                )
            } else {
                return InstallSourceInfo(
                    context.packageManager.getInstallerPackageName(context.packageName).toString()
                )
            }
        }

    val applicationId: String
        get() = context.packageName

    private fun getSignatures(): List<Signature> {
        return if (OS.supportsGetSigningCerts()) {
            getModernSignatures()
        } else {
            getLegacySignatures()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun getModernSignatures(): List<Signature> {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )

        val signingInfo = packageInfo.signingInfo ?: return emptyList()

        return if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners?.toList().orEmpty()
        } else {
            signingInfo.signingCertificateHistory?.toList().orEmpty()
        }
    }

    @Suppress("DEPRECATION")
    private fun getLegacySignatures(): List<Signature> {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        )

        return packageInfo.signatures?.toList().orEmpty()
    }

    private fun computeSignatureHash(signature: Signature): String {
        return Encodings.bytesToHex(
            Hashes.sha256(signature.toByteArray())
        )
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(): PackageInfo? {
        val apkPath = context.applicationInfo.sourceDir
        return context.packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
    }
}
