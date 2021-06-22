package io.muun.apollo.presentation.ui.utils

import android.os.Build

/**
 * Utility object to group Device related queries or operation like checking supported features
 * based on Android version.
 */
object Device {

    fun supportsImageDecoderApi() =
        isAndroidPOrNewer()

    private fun isAndroidPOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
}