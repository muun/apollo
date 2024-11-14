package io.muun.apollo.presentation.ui.security_logout;

import io.muun.apollo.domain.action.session.LogoutAction;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.errors.MuunError;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.Optional;

import android.os.Bundle;
import androidx.annotation.Nullable;
import timber.log.Timber;

import javax.inject.Inject;

@PerActivity
public class SecurityLogoutPresenter extends BasePresenter<BaseView> {

    private final LogoutAction logout;

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
        // TODO this should probably happen BEFORE navigating to this screen, right?
        logout.run();
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

    private String getJwt() {
        final Optional<String> serverJwt = authRepository.getServerJwt();
        if (!serverJwt.isPresent()) {
            // Shouldn't happen but we wanna know 'cause probably a bug
            Timber.e(new MuunError("Auth token expected to be present"));
            return "";
        }

        return serverJwt.get();
    }

}
