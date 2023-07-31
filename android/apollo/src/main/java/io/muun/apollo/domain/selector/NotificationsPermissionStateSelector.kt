package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.permission.NotificationPermissionStateRepository
import io.muun.apollo.domain.model.PermissionState
import javax.inject.Inject

class NotificationsPermissionStateSelector @Inject constructor(
    private val notificationPermissionStateRepository: NotificationPermissionStateRepository,
) {

    fun get(): PermissionState =
        notificationPermissionStateRepository.get()
}