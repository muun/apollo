package io.muun.apollo.data.preferences

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.afs.Constants
import io.muun.apollo.data.afs.MetricsProvider
import timber.log.Timber
import javax.inject.Inject

/**
 * Records the ANDROID_ID this install sees, the second signal of an OEM device-to-device copy
 * next to the first install time (see [FirstInstallTimeRepository]).
 *
 * ANDROID_ID is per device and Android user (from Android 8, also per signing key) and survives
 * app updates and reinstalls; a copy can't carry it to another device. Preferences do travel
 * with such a copy, so a recorded value that disagrees with the current one is one of the two
 * signals DeviceCopyDetector needs to confirm it.
 */
class AndroidIdRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    private val metricsProvider: MetricsProvider,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        @VisibleForTesting
        internal const val ANDROID_ID_KEY = "android_id"
    }

    override val fileName get() = "android-id"

    /**
     * Write once, like [FirstInstallTimeRepository.recordCurrentInstallTimeIfAbsent]: only
     * [rebaseToCurrentInstall] replaces it. A value the device fails to report is never recorded:
     * it would read as a different device later on.
     */
    @SuppressLint("ApplySharedPref") // a crash later in onCreate must not lose the write
    fun recordCurrentAndroidIdIfAbsent() {
        if (sharedPreferences.contains(ANDROID_ID_KEY)) {
            return
        }

        val androidId = currentAndroidId() ?: return

        sharedPreferences.edit()
            .putString(ANDROID_ID_KEY, androidId)
            .commit()
    }

    /**
     * Replace the recorded value with this install's, like
     * [FirstInstallTimeRepository.rebaseToCurrentInstall].
     */
    @SuppressLint("ApplySharedPref") // a crash after the wallet data wipe must not lose it
    fun rebaseToCurrentInstall() {
        val androidId = currentAndroidId()
        val editor = sharedPreferences.edit()

        if (androidId != null) {
            editor.putString(ANDROID_ID_KEY, androidId)
        } else {
            editor.remove(ANDROID_ID_KEY)
        }

        editor.commit()
    }

    /**
     * The value recorded in this device's preferences (by the install a copy came from, if any),
     * or null when there's none. A value it never records (blank or UNKNOWN) reads as none too,
     * should the preference hold one anyway.
     */
    fun recordedAndroidId(): String? {
        return sharedPreferences.getString(ANDROID_ID_KEY, null)?.takeIf { isKnown(it) }
    }

    /**
     * What the device reports as this install's ANDROID_ID now, or null when it can't tell.
     */
    fun currentAndroidId(): String? {
        val androidId = try {
            metricsProvider.androidId
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }

        return androidId.takeIf { isKnown(it) }
    }

    // The provider reports a failed read as UNKNOWN: that's no value, not a device's value.
    private fun isKnown(androidId: String): Boolean {
        return androidId.isNotBlank() && androidId != Constants.UNKNOWN
    }
}
