package io.muun.apollo.domain

import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.preferences.BackgroundTimesRepository
import javax.inject.Inject

class BackgroundTimesProcessor @Inject constructor(
    private val backgroundTimesRepository: BackgroundTimesRepository,
) {

    companion object {
        @VisibleForTesting
        val MAX_BKG_TIMES_ARRAY_SIZE: Int = 100
    }

    fun enterBackground() {
        backgroundTimesRepository.recordEnterBackground()
    }

    fun enterForeground() {
        backgroundTimesRepository.pruneIfGreaterThan(MAX_BKG_TIMES_ARRAY_SIZE)

        val backgroundBeginTime = backgroundTimesRepository.getLastBackgroundBeginTime()
        @Suppress("FoldInitializerAndIfToElvis")
        if (backgroundBeginTime == null) {
            return
        }

        val duration = System.currentTimeMillis() - backgroundBeginTime
        backgroundTimesRepository.recordBackgroundEvent(backgroundBeginTime, duration)
    }
}