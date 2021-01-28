package io.muun.apollo.presentation.ui.feedback.anon;

import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_SUPPORT_TYPE;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.Optional;

import android.os.Bundle;
import androidx.annotation.Nullable;
import icepick.State;

import javax.inject.Inject;

@PerActivity
public class AnonFeedbackPresenter extends BasePresenter<AnonFeedbackView> {

    @State
    @Nullable
    String supportId;

    /**
     * Constructor.
     */
    @Inject
    public AnonFeedbackPresenter(UserSelector userSel) {
        this.userSel = userSel;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);
        supportId = arguments.getString(AnonFeedbackView.SUPPORT_ID, null);

        view.setSupportId(Optional.ofNullable(supportId));
    }

    @Nullable
    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_SUPPORT(S_SUPPORT_TYPE.ANON_SUPPORT);
    }

    public void onOpenEmailClient() {
        navigator.navigateToEmailClient(getContext());
    }
}
