package io.muun.apollo.presentation.ui.fragments.landing;

import io.muun.apollo.domain.action.fcm.ForceFetchFcmAction;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.signup.SignupPresenter;

import javax.inject.Inject;

@PerFragment
public class LandingPresenter extends SingleFragmentPresenter<SingleFragmentView, SignupPresenter> {

    private final ForceFetchFcmAction forceFetchFcm;

    @Inject
    public LandingPresenter(ForceFetchFcmAction forceFetchFcm) {
        this.forceFetchFcm = forceFetchFcm;
    }

    @Override
    public void afterSetUp() {
        super.afterSetUp();
        getParentPresenter().resumeSignupIfStarted();
        assertGooglePlayServicesPresent();

        // "Fire and forget" attempt to retrieve FCM token so its already available at signup/signin
        forceFetchFcm.run();
    }

    public void startSignup() {
        getParentPresenter().startSignup();
    }

    public void startLogin() {
        getParentPresenter().startLogin();
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_GET_STARTED();
    }
}
