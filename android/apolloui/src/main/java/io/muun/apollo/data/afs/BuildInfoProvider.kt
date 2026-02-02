package io.muun.apollo.data.afs

import android.os.Build
import io.muun.apollo.data.os.OS

class BuildInfoProvider {

    val buildInfo: BuildInfo
        get() {
            return BuildInfo(
                getABIs(),
                Build.BOOTLOADER,
                Build.MANUFACTURER,
                Build.BRAND,
                Build.DISPLAY,
                Build.HOST,
                Build.TYPE,
                Build.getRadioVersion(),
                getSecurityPatch(),
                Build.MODEL,
                Build.PRODUCT,
                Build.VERSION.RELEASE,
                AfsUtils.epochAtUtcMidnight(Build.TIME)
            )
        }

    val deviceName: String
        get() = Build.DEVICE

    val deviceModel: String
        get() = Build.MODEL

    val sdkLevel: Int
        get() = Build.VERSION.SDK_INT

    private fun getABIs(): List<String> {
        return Build.SUPPORTED_ABIS.toList()
    }

    private fun getSecurityPatch(): String {
        return if (OS.supportsBuildVersionSecurityPatch()) {
            Build.VERSION.SECURITY_PATCH
        } else {
            Constants.UNKNOWN
        }
    }
}