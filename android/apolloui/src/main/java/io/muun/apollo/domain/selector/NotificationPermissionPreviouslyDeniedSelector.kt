package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.NotificationPermissionDeniedRepository
import javax.inject.Inject

class NotificationPermissionPreviouslyDeniedSelector @Inject constructor(
    private val notificationPermissionDeniedRepository: NotificationPermissionDeniedRepository,
) {

    fun get(): Boolean =
        notificationPermissionDeniedRepository.hasPreviouslyDeniedNotificationPermission()
}