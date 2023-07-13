package io.muun.apollo.data.os

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.gms.common.GoogleApiAvailability
import javax.inject.Inject

class GooglePlayHelper @Inject constructor(context: Context) {

    private val packageInfo = context.packageManager.getPackageInfo(
        GoogleApiAvailability.GOOGLE_PLAY_STORE_PACKAGE,
        0
    )

    /**
     * System's Google Play store version code.
     */
    val versionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(packageInfo)

    /**
     * System's Google Play store version name.
     */
    val versionName: String
        get() = packageInfo.versionName

    fun getPlayInfo(): PlayInfo =
        PlayInfo(versionCode, versionName)

    data class PlayInfo(
        val versionCode: Long,
        val versionName: String,
    )
}