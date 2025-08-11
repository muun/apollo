package io.muun.apollo.domain.action.permission

import io.muun.apollo.data.preferences.permission.NotificationPermissionStateRepository
import io.muun.apollo.domain.model.PermissionState
import javax.inject.Inject

class SetNotificationPermissionNeverAskAgainAction @Inject constructor(
    private val notificationPermissionStateRepository: NotificationPermissionStateRepository,
) {

    fun run() {
        return notificationPermissionStateRepository.store(PermissionState.PERMANENTLY_DENIED)
    }
}