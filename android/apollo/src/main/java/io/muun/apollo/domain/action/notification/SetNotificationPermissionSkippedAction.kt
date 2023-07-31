package io.muun.apollo.domain.action.notification

import io.muun.apollo.data.preferences.NotificationPermissionSkippedRepository
import javax.inject.Inject

class SetNotificationPermissionSkippedAction @Inject constructor(
    private val notificationPermissionSkippedRepository: NotificationPermissionSkippedRepository,
) {

    fun run() {
        return notificationPermissionSkippedRepository.store(true)
    }
}