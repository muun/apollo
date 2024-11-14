package io.muun.apollo.data.os

import android.os.Build
import kotlinx.serialization.Serializable
import javax.inject.Inject

class BuildInfoProvider @Inject constructor() {

    @Serializable
    data class BuildInfo(
        val abis: List<String>,
        val fingerprint: String,
        val hardware: String,
        val bootloader: String,
        val manufacturer: String,
        val brand: String,
        val display: String,
        val time: Long,
        val host: String,
        val type: String,
        val radioVersion: String,
        val securityPatch: String,
        val baseOs: String,
    )

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