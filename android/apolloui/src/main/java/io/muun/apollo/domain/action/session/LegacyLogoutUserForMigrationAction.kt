package io.muun.apollo.domain.action.session

import io.muun.apollo.data.async.tasks.TaskScheduler
import io.muun.apollo.data.db.DaoManager
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.domain.ApplicationLockManager
import io.muun.apollo.domain.action.ContactActions
import io.muun.apollo.domain.action.base.AsyncActionStore
import javax.inject.Inject

class LegacyLogoutUserForMigrationAction @Inject constructor(
    private val taskScheduler: TaskScheduler,
    private val asyncActionStore: AsyncActionStore,
    private val secureStorageProvider: SecureStorageProvider,
    private val contactActions: ContactActions,
    private val daoManager: DaoManager,
    private val lockManager: ApplicationLockManager,
    private val notificationService: NotificationService,
    private val clearRepositories: ClearRepositoriesAction,
) {

    fun run() {
        taskScheduler.unscheduleAllTasks()
        asyncActionStore.resetAll()
        secureStorageProvider.wipe()

        // Note: using this to avoid copying a lot of code. Also repository clear() is a safe op
        // (if we clear a repo that didn't exist in the past it doesn't hurt).
        clearRepositories.clearAll()

        contactActions.stopWatchingContacts()
        daoManager.delete()

        lockManager.cancelAutoSetLocked()

        notificationService.cancelAllNotifications()
    }
}