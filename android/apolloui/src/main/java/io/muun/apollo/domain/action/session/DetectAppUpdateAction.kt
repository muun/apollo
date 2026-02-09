package io.muun.apollo.domain.action.session

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.preferences.AppVersionRepository
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import timber.log.Timber
import javax.inject.Inject

class DetectAppUpdateAction @Inject constructor(
    private val analytics: Analytics,
    private val repo: AppVersionRepository,
) {

    fun run() {
        val currentVersion = Globals.INSTANCE.versionCode
        val currentVersionName = Globals.INSTANCE.versionName
        if (currentVersion > repo.getVersionCode()) {
            // Leaving this legacy log (and tracking just in case there's queries using it).
            Timber.i("App update: ${repo.getVersionCode()} -> $currentVersion")

            if (repo.getVersionCode() == 0) {
                // This is an app install. Analytics correctly tracks 'first_open'.
            } else {
                // This is an app update. Analytics 'app_update' seems super unreliable, so...
                analytics.report(
                    AnalyticsEvent.E_APP_UPDATE(
                        repo.getVersionCode(),
                        repo.getVersionName() ?: "null",
                        currentVersion,
                        currentVersionName
                    )
                )
            }

            repo.update(currentVersion, currentVersionName)

        } else if (currentVersion < repo.getVersionCode()) {
            Timber.e(RuntimeException("App version downgrade detected! This shouldn't happen!"))
        }
    }
}