package io.muun.apollo.domain.model

/**
 * If OS.supportsInstallSourceInfo() (Api level 30+) contains 3 pieces of data.
 * Else just install source.
 */
data class InstallSourceInfo(
    val installingPackageName: String,
    val initiatingPackageName: String? = null, //If !OS.supportsInstallSourceInfo()
    val initiatingPackageSigningInfo: String? = null, // If !OS.supportsInstallSourceInfo()
)