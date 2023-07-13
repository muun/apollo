package io.muun.apollo.presentation.ui.fragments.ek_success;

import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.analytics.AnalyticsEvent.FEEDBACK_TYPE;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionPresenter;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter;

import javax.inject.Inject;

@PerFragment
public class EmergencyKitSuccessPresenter
        extends SingleActionPresenter<BaseView, SetupRecoveryCodePresenter> {

    @Inject
    public EmergencyKitSuccessPresenter() {
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_FEEDBACK(FEEDBACK_TYPE.EMERGENCY_KIT_SUCCESS);
    }
}
