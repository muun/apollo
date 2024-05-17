package io.muun.apollo.data.os

import android.content.Context
import io.muun.apollo.domain.model.InstallSourceInfo

fun Context.getInstallSourceInfo(): InstallSourceInfo =
    if (OS.supportsInstallSourceInfo()) {
        val installSourceInfo = packageManager.getInstallSourceInfo(this.packageName)
        // Not using originatingPackageName since we don't have INSTALL_PACKAGES permission
        // See: https://developer.android.com/reference/android/content/pm/PackageManager#getInstallSourceInfo(java.lang.String)
        val installingPackageName = installSourceInfo.installingPackageName
        val initiatingPackageSigningInfo = installSourceInfo.initiatingPackageSigningInfo.toString()
        val initiatingPackageName = installSourceInfo.initiatingPackageName

        InstallSourceInfo(
            installingPackageName.toString(),
            initiatingPackageName,
            initiatingPackageSigningInfo
        )
    } else {
        InstallSourceInfo(packageManager.getInstallerPackageName(this.packageName).toString())
    }