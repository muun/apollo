package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

class NotificationPermissionDeniedRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val NOTIFICATION_PERMISSION_DENIED = "notification_permission_denied"
    }

    private val permissionDeniedPref: Preference<Boolean> = rxSharedPreferences
        .getBoolean(NOTIFICATION_PERMISSION_DENIED)

    override fun getFileName() =
        "notification_permission_denied"

    fun setHasPreviouslyDeniedNotificationPermission() {
        permissionDeniedPref.set(true)
    }

    fun hasPreviouslyDeniedNotificationPermission(): Boolean =
        permissionDeniedPref.get()!!
}