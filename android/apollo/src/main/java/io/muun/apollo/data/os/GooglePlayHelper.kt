package io.muun.apollo.data.os

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.gms.common.GoogleApiAvailability
import timber.log.Timber
import javax.inject.Inject

class GooglePlayHelper @Inject constructor(ctx: Context) {

    private val packageInfo = try {
        ctx.packageManager.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_STORE_PACKAGE, 0)
    } catch (e: java.lang.Exception) {
        if (e !is PackageManager.NameNotFoundException) {
            Timber.e(e)
        }
        null
    }

    /**
     * System's Google Play store version code.
     */
    val versionCode: Long
        get() = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1L

    /**
     * System's Google Play store version name.
     */
    val versionName: String
        get() = packageInfo?.versionName ?: "UNKNOWN"

    fun getPlayInfo(): PlayInfo =
        PlayInfo(versionCode, versionName)

    data class PlayInfo(
        val versionCode: Long,
        val versionName: String,
    )
}