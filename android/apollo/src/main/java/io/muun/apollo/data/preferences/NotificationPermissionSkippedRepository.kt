package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

class NotificationPermissionSkippedRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val NOTIFICATION_PERMISSION_SKIPPED = "notification_permission_skipped"
    }

    private val permissionSkippedPref: Preference<Boolean> = rxSharedPreferences
        .getBoolean(NOTIFICATION_PERMISSION_SKIPPED)

    override fun getFileName() =
        "notification_permission_skipped"

    fun store(permissionSkipped: Boolean) {
        permissionSkippedPref.set(permissionSkipped)
    }

    fun get(): Boolean =
        permissionSkippedPref.get()!!
}