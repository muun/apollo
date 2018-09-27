package io.muun.apollo.domain.action;

import io.muun.apollo.data.async.TaskScheduler;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.preferences.migration.PreferencesMigrationManager;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.NotificationService;
import io.muun.apollo.domain.action.base.AsyncActionStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LogoutActions {

    private final TaskScheduler taskScheduler;

    private final AsyncActionStore asyncActionStore;

    private final DaoManager daoManager;

    private final SecureStorageProvider secureStorageProvider;

    private final PreferencesMigrationManager preferencesMigrationManager;

    private final ContactActions contactActions;

    private final NotificationService notificationService;

    private final ApplicationLockManager lockManager;

    /**
     * Constructor.
     */
    @Inject
    public LogoutActions(AsyncActionStore asyncActionStore,
                         DaoManager daoManager,
                         TaskScheduler taskScheduler,
                         SecureStorageProvider secureStorageProvider,
                         PreferencesMigrationManager preferencesMigrationManager,
                         NotificationService notificationService,
                         ContactActions contactActions,
                         ApplicationLockManager lockManager) {

        this.asyncActionStore = asyncActionStore;
        this.daoManager = daoManager;
        this.taskScheduler = taskScheduler;
        this.secureStorageProvider = secureStorageProvider;
        this.preferencesMigrationManager = preferencesMigrationManager;
        this.lockManager = lockManager;
        this.contactActions = contactActions;
        this.notificationService = notificationService;
    }

    /**
     * Wipes all user associated data from the app.
     */
    public void logout() {

        taskScheduler.unscheduleAllTasks();
        asyncActionStore.resetAll();
        secureStorageProvider.wipe();

        clearAllRepositories();
        contactActions.stopWatchingContacts();
        daoManager.delete();

        lockManager.cancelAutoSetLocked();

        notificationService.cancelAllNotifications();
    }

    private void clearAllRepositories() {
        preferencesMigrationManager.clearAllRepositories();
    }
}
