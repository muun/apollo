package io.muun.apollo.domain.action;

import io.muun.apollo.data.async.tasks.TaskScheduler;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.SignupDraftManager;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.session.ClearRepositoriesAction;
import io.muun.apollo.domain.action.session.RebaseInstallBaselinesAction;
import io.muun.apollo.domain.errors.SecureKeyValueStoreError;
import io.muun.apollo.domain.errors.UnrecoverableUserLogoutError;
import io.muun.apollo.domain.libwallet.LibwalletClient;
import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.domain.selector.LogoutOptionsSelector;
import io.muun.apollo.domain.selector.LogoutOptionsSelector.LogoutOptions;
import io.muun.common.utils.Preconditions;

import androidx.annotation.Nullable;
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

    private final SecureKeyValueStoreRepository secureKeyValueStoreRepository;

    private final NotificationService notificationService;

    private final LibwalletClient libwalletClient;

    private final UserRepository userRepository;

    private final AuthRepository authRepository;

    private final RebaseInstallBaselinesAction rebaseInstallBaselines;

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
            SecureKeyValueStoreRepository secureKeyValueStoreRepository,
            NotificationService notificationService,
            LibwalletClient libwalletClient,
            UserRepository userRepository,
            AuthRepository authRepository,
            RebaseInstallBaselinesAction rebaseInstallBaselines
    ) {

        this.contactActions = contactActions;
        this.asyncActionStore = asyncActionStore;
        this.daoManager = daoManager;
        this.lockManager = lockManager;
        this.logoutOptionsSel = logoutOptionsSel;
        this.signupDraftManager = signupDraftManager;
        this.clearRepositories = clearRepositories;

        this.taskScheduler = taskScheduler;
        this.secureKeyValueStoreRepository = secureKeyValueStoreRepository;
        this.notificationService = notificationService;
        this.libwalletClient = libwalletClient;
        this.userRepository = userRepository;
        this.authRepository = authRepository;
        this.rebaseInstallBaselines = rebaseInstallBaselines;
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
        final SignupDraft signupDraft = fetchSignupDraftToPreserve();
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

    /**
     * Preserving the draft is best-effort: if it can't be read, the wipe goes ahead without it,
     * since the wipe is what clears a secure storage that can't be read.
     */
    @Nullable
    private SignupDraft fetchSignupDraftToPreserve() {
        try {
            return signupDraftManager.fetchSignupDraft();
        } catch (SecureKeyValueStoreError e) {
            Timber.e(e);
            return null;
        }
    }

    private void destroyWallet() {
        Timber.i("destroyWallet: %s", describeWipedSession());
        taskScheduler.unscheduleAllTasks();

        asyncActionStore.resetAllExceptLogout();

        try {
            secureKeyValueStoreRepository.wipe();
        } finally {
            // Whatever the wipe managed to remove, the next wallet here belongs to this install.
            // A failed wipe still rebases: missing that copy is the safe way to fail, flagging
            // the next wallet here is not. After the wipe, not before: dying in between must not
            // leave a copied wallet with this install's baselines. Not at the end of this method:
            // anything below can throw.
            rebaseInstallBaselines.run();
        }

        clearRepositories.clearForLogout();
        contactActions.stopWatchingContacts();
        daoManager.delete();

        lockManager.cancelAutoSetLocked();

        notificationService.cancelAllNotifications();
        libwalletClient.resetData();
    }

    /**
     * Describe whose data a wipe destroys, for the destroyWallet breadcrumb. Never throws: failing
     * to read the stored user or session must not stop the wipe.
     */
    private String describeWipedSession() {
        try {
            final Long hid = userRepository.fetchHid();
            final String sessionStatus = authRepository.getSessionStatus()
                    .map(Enum::name)
                    .orElse("none");

            return "hid=" + (hid != null ? hid : "none") + ", session=" + sessionStatus;

        } catch (Throwable error) {
            return "unknown (" + error.getClass().getSimpleName() + ")";
        }
    }
}
