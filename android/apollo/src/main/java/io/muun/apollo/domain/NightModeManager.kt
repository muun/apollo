package io.muun.apollo.domain

import io.muun.apollo.data.preferences.NightModeRepository
import io.muun.apollo.domain.model.NightMode
import javax.inject.Inject

class NightModeManager @Inject constructor(private val repo: NightModeRepository) {

    fun get(): NightMode =
        repo.getNightMode()

    fun save(mode: NightMode) {
        repo.storeNightMode(mode)
    }
}