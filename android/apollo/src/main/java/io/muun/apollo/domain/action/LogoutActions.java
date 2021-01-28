package io.muun.apollo.domain.action;

import io.muun.apollo.data.async.tasks.TaskScheduler;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.fs.LibwalletDataDirectory;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.BaseRepository;
import io.muun.apollo.data.preferences.ClientVersionRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FcmTokenRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.NotificationRepository;
import io.muun.apollo.data.preferences.SchemaVersionRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.errors.UnrecoverableUserLogoutError;
import io.muun.apollo.domain.selector.LogoutOptionsSelector;
import io.muun.apollo.domain.selector.LogoutOptionsSelector.LogoutOptions;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

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

    private final LogoutOptionsSelector logoutOptionsSel;

    private final List<BaseRepository> repositoriesToClear;
    private final List<String> thirdPartyPreferencesToClear;

    private final FcmTokenRepository fcmTokenRepository;

    private final LibwalletDataDirectory libwalletDataDirectory;

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
                         LogoutOptionsSelector logoutOptionsSel,
                         AuthRepository authRepository,
                         ExchangeRateWindowRepository exchangeRateWindowRepository,
                         KeysRepository keysRepository,
                         UserRepository userRepository,
                         SchemaVersionRepository schemaVersionRepository,
                         FeeWindowRepository feeWindowRepository,
                         ClientVersionRepository clientVersionRepository,
                         NotificationRepository notificationRepository,
                         TransactionSizeRepository transactionSizeRepository,
                         FcmTokenRepository fcmTokenRepository,
                         LibwalletDataDirectory libwalletDataDirectory) {

        this.context = context;
        this.asyncActionStore = asyncActionStore;
        this.daoManager = daoManager;
        this.taskScheduler = taskScheduler;
        this.secureStorageProvider = secureStorageProvider;
        this.lockManager = lockManager;
        this.contactActions = contactActions;
        this.notificationService = notificationService;
        this.logoutOptionsSel = logoutOptionsSel;

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

        this.fcmTokenRepository = fcmTokenRepository;
        this.libwalletDataDirectory = libwalletDataDirectory;

    }

    /**
     * Wipes all user associated data from the app (recoverable only).
     */
    public void destroyRecoverableWallet() {
        final LogoutOptions logoutOptions = logoutOptionsSel.get();

        if (logoutOptions.wouldDeleteWallet()) {
            Timber.e(new UnrecoverableUserLogoutError());
            return; // should never happen, but if a bug causes this NEVER delete storage
        }

        destroyWallet();
    }

    /**
     * Wipe all user associated data from the app (unrecoverable only).
     */
    public void dangerouslyDestroyUnrecoverableWallet() {
        final LogoutOptions logoutOptions = logoutOptionsSel.get();
        Preconditions.checkState(!logoutOptions.isBlocked()); // just checking

        destroyWallet();
    }

    /**
     * Wipe all user associated data from the app.
     * As the name suggests, this method performs no checks and dangerously destroy all user data in
     * a final and unrecoverable way. It is public for the only use of clearing data between
     * UiTests and MUST NOT be used otherwise.
     */
    @VisibleForTesting
    public void uncheckedDestroyWalletForUiTests() {
        Preconditions.checkArgument(isTesting());
        destroyWallet();
    }

    /**
     * This is a, yes pretty hacky, but also VERY convenient and EFFECTIVE way of distinguishing if
     * app is running normally or if its being runned by an instrumention test (aka ui test). The
     * basic idea is that we search for a class that would only be present in a test classpath.
     * Should NEVER be used for application business logic, rather we can make use of it to assert
     * some stuff that may only/never execute when on a ui test.
     */
    private boolean isTesting() {
        try {
            Class.forName("io.muun.apollo.utils.AutoFlows");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void destroyWallet() {
        taskScheduler.unscheduleAllTasks();

        asyncActionStore.resetAllExceptLogout();
        secureStorageProvider.wipe();

        clearRepositoriesForLogout();
        contactActions.stopWatchingContacts();
        daoManager.delete();

        lockManager.cancelAutoSetLocked();

        notificationService.cancelAllNotifications();
        libwalletDataDirectory.reset();
    }

    /**
     * Destroy all data in non-encrypted repositories.
     * Warning: this method is supposed to be used solely in PreferencesMigrationManager, since we
     * shouldn't be needing any "full wipe preference migration" anymore, this method is left just
     * for the sake of completeness.
     */
    public void clearAllRepositories() {
        clearRepositoriesForLogout();
        clearRepository(fcmTokenRepository);
    }

    /**
     * Destroy all data in non-encrypted repositories that should be cleared upon logout. We avoid
     * clearing some repositories on logout (e.g FcmTokenRepository).
     */
    private void clearRepositoriesForLogout() {
        for (BaseRepository repository : repositoriesToClear) {
            clearRepository(repository);
        }

        for (String fileName : thirdPartyPreferencesToClear) {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply();
        }
    }

    private void clearRepository(BaseRepository repository) {
        try {
            repository.clear();
        } catch (Exception e) {
            Timber.e(e);
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
