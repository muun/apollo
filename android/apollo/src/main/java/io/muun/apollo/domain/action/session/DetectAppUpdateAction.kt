package io.muun.apollo.domain.action.session

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.preferences.AppVersionRepository
import timber.log.Timber
import javax.inject.Inject

class DetectAppUpdateAction @Inject constructor(private val repo: AppVersionRepository) {

    fun run() {
        val currentVersion = Globals.INSTANCE.versionCode
        if (currentVersion > repo.getVersion()) {
            Timber.i("App update: ${repo.getVersion()} -> $currentVersion")
            repo.update(currentVersion)

        } else if (currentVersion < repo.getVersion()) {
            Timber.e(RuntimeException("App version downgrade detected! This shouldn't happen!"))
        }
    }
}