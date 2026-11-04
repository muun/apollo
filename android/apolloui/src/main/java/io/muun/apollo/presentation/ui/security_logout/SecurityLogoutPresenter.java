package io.muun.apollo.presentation.ui.security_logout;

import io.muun.apollo.domain.action.session.LogoutAction;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import android.os.Bundle;
import androidx.annotation.Nullable;
import icepick.State;

import javax.inject.Inject;

@PerActivity
public class SecurityLogoutPresenter extends BasePresenter<BaseView> {

    private final LogoutAction logout;

    // setUp runs on every resume, and a second logout would find the wallet already wiped.
    @State
    boolean loggedOut;

    /**
     * Constructor.
     */
    @Inject
    public SecurityLogoutPresenter(LogoutAction logout) {
        this.logout = logout;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);
        if (!loggedOut) {
            logout.run();
            loggedOut = true;
        }
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
