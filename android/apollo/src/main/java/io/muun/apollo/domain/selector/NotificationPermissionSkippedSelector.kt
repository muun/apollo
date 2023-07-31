package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.NotificationPermissionSkippedRepository
import javax.inject.Inject

class NotificationPermissionSkippedSelector @Inject constructor(
    private val notificationPermissionSkippedRepository: NotificationPermissionSkippedRepository,
) {

    fun get(): Boolean =
        notificationPermissionSkippedRepository.get()
}