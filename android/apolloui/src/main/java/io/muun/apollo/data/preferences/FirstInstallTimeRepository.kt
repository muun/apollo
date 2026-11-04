package io.muun.apollo.data.preferences

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.afs.MetricsProvider
import timber.log.Timber
import javax.inject.Inject

/**
 * Records what PackageManager reports as this install's first install time.
 *
 * Preferences survive an OEM device-to-device copy and Android Keystore keys do not, so a
 * recorded value that disagrees with the current install is one of the two signals
 * DeviceCopyDetector needs to confirm such a copy.
 */
class FirstInstallTimeRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    private val metricsProvider: MetricsProvider,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        @VisibleForTesting
        internal const val FIRST_INSTALL_TIME_KEY = "first_install_time_in_ms"
    }

    override val fileName get() = "first-install-time"

    /**
     * Write once: an overwrite would replace the copied device's value on the first launch of
     * the new one, which is the moment the difference is there to be read. Only
     * [rebaseToCurrentInstall] replaces it. A time the device can't report is never recorded: it
     * would read as a different install later on.
     */
    @SuppressLint("ApplySharedPref") // a crash later in onCreate must not lose the write
    fun recordCurrentInstallTimeIfAbsent() {
        // An unknown value earlier versions recorded is no baseline: replace it with a real one.
        if (isKnown(sharedPreferences.getLong(FIRST_INSTALL_TIME_KEY, 0))) {
            return
        }

        val installTimeInMs = currentInstallTimeInMs() ?: return

        sharedPreferences.edit()
            .putLong(FIRST_INSTALL_TIME_KEY, installTimeInMs)
            .commit()
    }

    /**
     * Replace the recorded value with this install's, for when the wallet data on this device is
     * wiped: the next wallet here belongs to this install, and comparing it against the device a
     * copy came from would keep flagging it as a copy. Written now rather than just cleared:
     * logout doesn't restart the process, so a copy made before the next launch would otherwise
     * find no baseline.
     *
     * Removes the value when this install's can't be read, so that the next launch records it.
     */
    @SuppressLint("ApplySharedPref") // a crash after the wallet data wipe must not lose it
    fun rebaseToCurrentInstall() {
        val installTimeInMs = currentInstallTimeInMs()
        val editor = sharedPreferences.edit()

        if (installTimeInMs != null) {
            editor.putLong(FIRST_INSTALL_TIME_KEY, installTimeInMs)
        } else {
            editor.remove(FIRST_INSTALL_TIME_KEY)
        }

        editor.commit()
    }

    /**
     * The value recorded in this device's preferences (by the install a copy came from, if any),
     * or null when there's none. Earlier versions recorded whatever PackageManager reported, so a
     * recorded value at or before the epoch reads as none too.
     */
    fun recordedInstallTimeInMs(): Long? {
        return sharedPreferences.getLong(FIRST_INSTALL_TIME_KEY, 0).takeIf { isKnown(it) }
    }

    /**
     * What PackageManager reports for this install now, or null when it can't tell.
     */
    fun currentInstallTimeInMs(): Long? {
        val installTimeInMs = try {
            metricsProvider.firstInstallTimeInMs
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }

        return installTimeInMs.takeIf { isKnown(it) }
    }

    // No install happened at or before the epoch: such a value is a failed read, not a time.
    private fun isKnown(installTimeInMs: Long): Boolean {
        return installTimeInMs > 0
    }
}
