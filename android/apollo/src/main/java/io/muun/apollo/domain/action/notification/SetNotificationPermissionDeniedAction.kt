package io.muun.apollo.domain.action.notification

import io.muun.apollo.data.preferences.NotificationPermissionDeniedRepository
import javax.inject.Inject

class SetNotificationPermissionDeniedAction @Inject constructor(
    private val notificationPermissionDeniedRepository: NotificationPermissionDeniedRepository,
) {

    fun run() {
        return notificationPermissionDeniedRepository.setHasPreviouslyDeniedNotificationPermission()
    }
}