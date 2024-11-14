package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

class ApiMigrationsVersionRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(
    context, repositoryRegistry
) {
    companion object {
        private const val KEY_VERSION = "version"
    }

    private val versionPreference: Preference<Int>
        get() = rxSharedPreferences.getInteger(KEY_VERSION)

    override val fileName get() = "api_migrations_version"

    /**
     * Returns the stored version, or 0 if none is stored.
     */
    var version: Int
        get() = versionPreference.get()!!
        set(version) {
            versionPreference.set(version)
        }
}