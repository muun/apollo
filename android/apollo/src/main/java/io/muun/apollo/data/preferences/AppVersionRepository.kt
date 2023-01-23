package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

class AppVersionRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY = "current_app_version"
    }

    private val versionCodePreference: Preference<Int> = rxSharedPreferences.getInteger(
        KEY,
        0
    )

    override fun getFileName(): String =
        "current_app_version"

    fun update(newVersionCode: Int) {
        versionCodePreference.set(newVersionCode)
    }

    fun getVersion() =
        versionCodePreference.get()!!
}