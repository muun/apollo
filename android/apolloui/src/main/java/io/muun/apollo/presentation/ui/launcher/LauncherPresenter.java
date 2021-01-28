package io.muun.apollo.presentation.ui.launcher;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.preferences.ClientVersionRepository;
import io.muun.apollo.domain.ApiMigrationsManager;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.session.UseMuunLinkAction;
import io.muun.apollo.domain.errors.DeprecatedClientVersionError;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import android.net.Uri;
import androidx.annotation.Nullable;

import javax.inject.Inject;

@PerActivity
public class LauncherPresenter extends BasePresenter<BaseView> {

    private final UserActions userActions;

    private final UseMuunLinkAction useMuunLinkAction;

    private final ClientVersionRepository clientVersionRepository;

    private final ApiMigrationsManager apiMigrationsManager;

    /**
     * Creates launcher presenter.
     */
    @Inject
    public LauncherPresenter(UserActions userActions,
                             UseMuunLinkAction useMuunLinkAction,
                             ClientVersionRepository clientVersionRepository,
                             ApiMigrationsManager apiMigrationsManager) {

        this.userActions = userActions;
        this.useMuunLinkAction = useMuunLinkAction;
        this.clientVersionRepository = clientVersionRepository;
        this.apiMigrationsManager = apiMigrationsManager;
    }

    /**
     * Launch the application, whatever that implies in the current state.
     */
    public void handleLaunch(@Nullable Uri uri, boolean isTaskRoot) {

        final int minClientVersion = clientVersionRepository.getMinClientVersion().orElse(0);

        if (Globals.INSTANCE.getVersionCode() >= minClientVersion) {

            // If we caught an Intent with an URI (from an external Muun link), handle it:
            if (uri != null) {
                useMuunLinkAction.run(uri.toString());
            }

            // If this activity is the task root, then this is a "fresh start" and we need to
            // navigate to the proper activity (signUp, home, etc...). Else, it means there were
            // activities in the current task (e.g. in background), so we don't want to
            // follow the initial flow, but finish() and return to top activity in the task.
            if (isTaskRoot) {
                navigateToNextActivity();
            }

        } else {
            handleError(new DeprecatedClientVersionError());
        }

        // Since this Activity has the NoDisplay theme (see AndroidManifest), it *must* call
        // `finish()` before `onResume()` completes, or an Exception will be thrown by the runtime:
        view.finishActivity();
    }

    private void navigateToNextActivity() {
        if (userActions.isLoggedIn()) {
            if (apiMigrationsManager.hasPending()) {
                navigator.navigateToMigrations(getContext());
            } else {
                navigator.navigateToHome(getContext());
            }
        } else {
            navigator.navigateToSignup(getContext());
        }
    }

    @Override
    protected boolean shouldCheckClientState() {
        // When this activity is first launched (or launched after logout), the client has no
        // session. If we check for the session status right now, we'll enter a loop:
        // Expired session -> logout -> launcher -> expired session

        // So, we skip the check here and let the next activity handle it:
        return false;
    }
}
