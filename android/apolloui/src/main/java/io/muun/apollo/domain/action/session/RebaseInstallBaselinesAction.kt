package io.muun.apollo.domain.action.session

import io.muun.apollo.data.preferences.AndroidIdRepository
import io.muun.apollo.data.preferences.FirstInstallTimeRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Make this install the baseline a device copy is detected against, once the wallet data on
 * this device is wiped. Never throws: a logout must not fail over a copy signal.
 *
 * Every path that wipes secure storage has to run it. SecureKeyValueStore#wipe doesn't yet:
 * nothing calls it in production.
 */
class RebaseInstallBaselinesAction @Inject constructor(
    private val firstInstallTimeRepository: FirstInstallTimeRepository,
    private val androidIdRepository: AndroidIdRepository,
) {

    fun run() {
        rebase(firstInstallTimeRepository::rebaseToCurrentInstall)
        rebase(androidIdRepository::rebaseToCurrentInstall)
    }

    private fun rebase(rebaseBaseline: () -> Unit) {
        try {
            rebaseBaseline()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
