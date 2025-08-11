package io.muun.apollo.data.afs

import android.content.Context
import io.muun.apollo.data.preferences.BackgroundTimesRepository
import io.muun.apollo.domain.model.BackgroundEvent

class AppInfoProvider(
    private val context: Context,
    private val bkgTimesRepo: BackgroundTimesRepository,
) {
    val appDatadir: String
        get() {
            return context.applicationInfo.dataDir
        }

    val latestBackgroundTimes: List<BackgroundEvent>
        get() = bkgTimesRepo.latestBackgroundTimes
}