package io.muun.apollo.domain.action;

import io.muun.apollo.data.async.tasks.TaskScheduler;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.fs.LibwalletDataDirectory;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.SignupDraftManager;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.session.ClearRepositoriesAction;
import io.muun.apollo.domain.errors.UnrecoverableUserLogoutError;
import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.domain.selector.LogoutOptionsSelector;
import io.muun.apollo.domain.selector.LogoutOptionsSelector.LogoutOptions;
import io.muun.common.utils.Preconditions;

import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LogoutActions {

    // Domain
    private final ContactActions contactActions; // TODO should be dismembered (as this action bag)

    private final AsyncActionStore asyncActionStore;

    private final DaoManager daoManager;

    private final ApplicationLockManager lockManager;

    private final LogoutOptionsSelector logoutOptionsSel;

    private final SignupDraftManager signupDraftManager;

    private final ClearRepositoriesAction clearRepositories;

    // Data
    private final TaskScheduler taskScheduler;

    private final SecureStorageProvider secureStorageProvider;

    private final NotificationService notificationService;

    private final LibwalletDataDirectory libwalletDataDirectory;

    /**
     * Constructor.
     */
    @Inject
    public LogoutActions(
            ContactActions contactActions,
            AsyncActionStore asyncActionStore,
            DaoManager daoManager,
            ApplicationLockManager lockManager,
            LogoutOptionsSelector logoutOptionsSel,
            SignupDraftManager signupDraftManager,
            ClearRepositoriesAction clearRepositories,
            TaskScheduler taskScheduler,
            SecureStorageProvider secureStorageProvider,
            NotificationService notificationService,
            LibwalletDataDirectory libwalletDataDirectory
    ) {

        this.contactActions = contactActions;
        this.asyncActionStore = asyncActionStore;
        this.daoManager = daoManager;
        this.lockManager = lockManager;
        this.logoutOptionsSel = logoutOptionsSel;
        this.signupDraftManager = signupDraftManager;
        this.clearRepositories = clearRepositories;

        this.taskScheduler = taskScheduler;
        this.secureStorageProvider = secureStorageProvider;
        this.notificationService = notificationService;
        this.libwalletDataDirectory = libwalletDataDirectory;
    }

    /**
     * Wipes all user associated data from the app (recoverable only).
     */
    public void destroyRecoverableWallet() {
        if (!logoutOptionsSel.isRecoverable()) {
            Timber.e(new UnrecoverableUserLogoutError());
            return; // should never happen, but if a bug causes this NEVER delete storage
        }

        destroyWallet();
    }

    /**
     * Wipe all user associated data from the app.
     * Note: if user is unrecoverable or recoverable user has performed "delete wallet" this
     * action is irreversible (and its intended to be). We're naming this "dangerously" because
     * callers should be careful when calling this.
     */
    public void dangerouslyDestroyWallet() {
        final LogoutOptions logoutOptions = logoutOptionsSel.get();
        Preconditions.checkState(logoutOptions.canDeleteWallet()); // just checking

        destroyWallet();
    }

    /**
     * Wipe all user associated data from the app, prior to a signup/login.
     *
     * <p>NOTE: since we do this prior to createLoginSession AND createFirstSession (login and
     * signup, respectively), some problems can arise if this calls take too much time, or fail,
     * and the user leaves the app and comes back (activity can be destroyed and re-created). So, if
     * there's a signupDraft when doing this local storage wipe, we preserve it.
     */
    public void destroyWalletToStartClean() {
        final SignupDraft signupDraft = signupDraftManager.fetchSignupDraft();
        destroyWallet();
        if (signupDraft != null) {
            signupDraftManager.save(signupDraft);
        }
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
        Timber.i("destroyWallet");
        taskScheduler.unscheduleAllTasks();

        asyncActionStore.resetAllExceptLogout();
        secureStorageProvider.wipe();

        clearRepositories.clearForLogout();
        contactActions.stopWatchingContacts();
        daoManager.delete();

        lockManager.cancelAutoSetLocked();

        notificationService.cancelAllNotifications();
        libwalletDataDirectory.reset();
    }
}
