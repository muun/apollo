package io.muun.apollo.domain.action;

import io.muun.apollo.data.async.tasks.TaskScheduler;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.BaseRepository;
import io.muun.apollo.data.preferences.ClientVersionRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.NotificationRepository;
import io.muun.apollo.data.preferences.SchemaVersionRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.external.NotificationService;

import android.content.Context;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LogoutActions {

    private final Context context;

    private final TaskScheduler taskScheduler;

    private final AsyncActionStore asyncActionStore;

    private final DaoManager daoManager;

    private final SecureStorageProvider secureStorageProvider;

    private final ContactActions contactActions;

    private final NotificationService notificationService;

    private final ApplicationLockManager lockManager;

    private final List<BaseRepository> repositoriesToClear;
    private final List<String> thirdPartyPreferencesToClear;

    /**
     * Constructor.
     */
    @Inject
    public LogoutActions(Context context,
                         AsyncActionStore asyncActionStore,
                         DaoManager daoManager,
                         TaskScheduler taskScheduler,
                         SecureStorageProvider secureStorageProvider,
                         NotificationService notificationService,
                         ContactActions contactActions,
                         ApplicationLockManager lockManager,
                         AuthRepository authRepository,
                         ExchangeRateWindowRepository exchangeRateWindowRepository,
                         KeysRepository keysRepository,
                         UserRepository userRepository,
                         SchemaVersionRepository schemaVersionRepository,
                         FeeWindowRepository feeWindowRepository,
                         ClientVersionRepository clientVersionRepository,
                         NotificationRepository notificationRepository,
                         TransactionSizeRepository transactionSizeRepository) {

        this.context = context;
        this.asyncActionStore = asyncActionStore;
        this.daoManager = daoManager;
        this.taskScheduler = taskScheduler;
        this.secureStorageProvider = secureStorageProvider;
        this.lockManager = lockManager;
        this.contactActions = contactActions;
        this.notificationService = notificationService;

        this.thirdPartyPreferencesToClear = createThirdPartyPreferencesList();

        this.repositoriesToClear = Arrays.asList(
                authRepository,
                exchangeRateWindowRepository,
                keysRepository,
                userRepository,
                schemaVersionRepository,
                feeWindowRepository,
                clientVersionRepository,
                notificationRepository,
                transactionSizeRepository
        );
    }

    /**
     * Wipes all user associated data from the app.
     */
    public void logout() {

        taskScheduler.unscheduleAllTasks();

        asyncActionStore.resetAllExceptLogout();
        secureStorageProvider.wipe();

        clearAllRepositories();
        contactActions.stopWatchingContacts();
        daoManager.delete();

        lockManager.cancelAutoSetLocked();

        notificationService.cancelAllNotifications();
    }

    /**
     * Destroy all data in non-encrypted repositories.
     */
    public void clearAllRepositories() {
        for (BaseRepository repository : repositoriesToClear) {
            try {
                repository.clear();
            } catch (Exception e) {
                Logger.error(e);
            }
        }

        for (String fileName : thirdPartyPreferencesToClear) {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply();
        }
    }

    private List<String> createThirdPartyPreferencesList() {
        // NOTE: there is no reliable way of listing all SharedPreferences. The XML files are
        // *usually* located in a known directory, but some vendors change this. We cannot control
        // where the directory lies in a particular device, but we can know and control the actual
        // preferences created by Apollo's dependencies.

        // The following list was created by logging into Apollo, and listing the XML files created
        // in the data/shared_prefs folder of the application.

        // ON-RELEASE verify that the list matches the actual files added by our dependencies. This
        // won't crash if files do not exist, but we could miss some.
        return Arrays.asList(
            "TwitterAdvertisingInfoPreferences",
            "WebViewChromiumPrefs",
            "com.crashlytics.prefs",
            "com.crashlytics.sdk.android:answers:settings",
            "com.crashlytics.sdk.android.crashlytics-core"
                + ":com.crashlytics.android.core.CrashlyticsCore",

            "com.google.android.gms.appid",
            "com.google.android.gms.measurement.prefs",
            "io.fabric.sdk.android:fabric:io.fabric.sdk.android.Onboarding"
        );
    }
}
