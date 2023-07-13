package io.muun.apollo.presentation.ui.security_logout;

import io.muun.apollo.domain.action.LogoutActions;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import android.os.Bundle;
import androidx.annotation.Nullable;

import javax.inject.Inject;

@PerActivity
public class SecurityLogoutPresenter extends BasePresenter<BaseView> {

    private final LogoutActions logoutActions;

    /**
     * Constructor.
     */
    @Inject
    public SecurityLogoutPresenter(LogoutActions logoutActions) {
        this.logoutActions = logoutActions;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);
        logoutActions.destroyRecoverableWallet();
    }

    /**
     * Navigate to Landing screen.
     */
    public void goToSignIn() {
        navigator.navigateToSignup(getContext());
        view.finishActivity();
    }

    @Nullable
    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_LOG_OUT();
    }
}
