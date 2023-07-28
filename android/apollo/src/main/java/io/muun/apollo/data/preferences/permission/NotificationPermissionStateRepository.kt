package io.muun.apollo.data.preferences.permission

import android.content.Context
import io.muun.apollo.data.preferences.BaseRepository
import io.muun.apollo.data.preferences.RepositoryRegistry
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.PermissionState
import javax.inject.Inject

class NotificationPermissionStateRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val NOTIFICATION_PERMISSION_STATE = "notification_permission_state"
    }

    private val permissionStatePref: Preference<PermissionState> = rxSharedPreferences.getEnum(
        NOTIFICATION_PERMISSION_STATE,
        PermissionState.NOT_DETERMINED,
        PermissionState::class.java
    )

    override fun getFileName() =
        "notification_permission_state"

    fun store(permissionState: PermissionState) {
        permissionStatePref.set(permissionState)
    }

    fun get(): PermissionState =
        permissionStatePref.get()!!
}