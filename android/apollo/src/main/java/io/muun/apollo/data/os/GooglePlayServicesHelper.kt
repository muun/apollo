package io.muun.apollo.data.os

import android.app.Activity
import android.content.Context
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

    /**
     * Check if Google Play Services is installed on the device.
     *
     * @return the result code, which will be AVAILABLE if successful.
     */
    val isAvailable: Int
        get() = apiAvailability.isGooglePlayServicesAvailable(context)

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