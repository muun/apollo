package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

/**
 * Why do we store current app version data you ask? Simple. To correctly detect app updates.
 */
class AppVersionRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val VERSION_CODE_KEY = "current_app_version"
        private const val VERSION_NAME_KEY = "current_app_version_name"
    }

    private val versionCodePreference: Preference<Int>
        get() = rxSharedPreferences.getInteger(VERSION_CODE_KEY, 0)

    private val versionNamePreference: Preference<String>
        get() = rxSharedPreferences.getString(VERSION_NAME_KEY, null)

    override val fileName get() = "current_app_version"

    fun update(newVersionCode: Int, newVersionName: String) {
        versionCodePreference.set(newVersionCode)
        versionNamePreference.set(newVersionName)
    }

    fun getVersionCode(): Int =
        versionCodePreference.get()!!

    fun getVersionName(): String? =
        versionNamePreference.get()
}