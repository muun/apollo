package io.muun.apollo.data.afs

import android.os.Build
import io.muun.apollo.data.os.OS

class BuildInfoProvider {

    val buildInfo: BuildInfo
        get() {
            return BuildInfo(
                getABIs(),
                Build.FINGERPRINT,
                Build.HARDWARE,
                Build.BOOTLOADER,
                Build.MANUFACTURER,
                Build.BRAND,
                Build.DISPLAY,
                Build.TIME,
                Build.HOST,
                Build.TYPE,
                Build.getRadioVersion(),
                getSecurityPatch(),
                getBaseOs()
            )
        }

    val deviceName: String
        get() = Build.DEVICE

    val deviceModel: String
        get() = Build.MODEL

    val sdkLevel: Int
        get() = Build.VERSION.SDK_INT

    private fun getABIs(): List<String> {
        return if (OS.supportsBuildSupportedAbis()) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            emptyList()
        }
    }

    private fun getSecurityPatch(): String {
        return if (OS.supportsBuildVersionSecurityPatch()) {
            Build.VERSION.SECURITY_PATCH
        } else {
            Constants.UNKNOWN
        }
    }

    private fun getBaseOs(): String {
        return if (OS.supportsBuildVersionBaseOs()) {
            Build.VERSION.BASE_OS
        } else {
            Constants.UNKNOWN
        }
    }
}