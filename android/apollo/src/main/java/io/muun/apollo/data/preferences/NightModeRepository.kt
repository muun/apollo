package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.NightMode
import javax.inject.Inject

class NightModeRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY = "current_night_mode"
    }

    private val nightModePreference: Preference<NightMode> = rxSharedPreferences.getEnum(
        KEY,
        NightMode.FOLLOW_SYSTEM,
        NightMode::class.java
    )

    override fun getFileName(): String =
        "current_night_mode"

    fun storeNightMode(mode: NightMode) {
        nightModePreference.set(mode)
    }

    fun getNightMode() =
        nightModePreference.get()!!

    fun watchNightMode() =
        nightModePreference.asObservable()
}