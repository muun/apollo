package io.muun.apollo.data.os

import android.app.Activity
import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import rx.functions.Action1
import javax.inject.Inject

class GooglePlayServicesHelper @Inject constructor(private val context: Context) {

    companion object {
        const val AVAILABLE = ConnectionResult.SUCCESS
        private const val PLAY_SERVICES_RESOLUTION_REQUEST = 9000
    }

    private val apiAvailability = GoogleApiAvailability.getInstance()

    private val packageInfo = context.packageManager.getPackageInfo(
        GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE,
        0
    )

    /**
     * Check if Google Play Services is installed on the device.
     *
     * @return the result code, which will be AVAILABLE if successful.
     */
    val isAvailable: Int
        get() = apiAvailability.isGooglePlayServicesAvailable(context)

    /**
     * System's Google Play Services version code.
     */
    val versionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(packageInfo)

    /**
     * System's Google Play Services version name.
     */
    val versionName: String
        get() = packageInfo.versionName

    /**
     * Google Play services client library version (aka our app's Google Play Services library
     * version code).
     */
    val clientVersionCode: Int
        get() = GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE

    fun getPlayServicesInfo(): PlayServicesInfo =
        PlayServicesInfo(versionCode, versionName, clientVersionCode)

    data class PlayServicesInfo(
        val versionCode: Long,
        val versionName: String,
        val clientVersionCode: Int,
    )

    /**
     * Display a dialog that allows the user to install Google Play Services.
     */
    fun showDownloadDialog(resultCode: Int): Action1<Activity> {
        return Action1 { activity: Activity ->
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability
                    .getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                    ?.show()
            }
        }
    }
}