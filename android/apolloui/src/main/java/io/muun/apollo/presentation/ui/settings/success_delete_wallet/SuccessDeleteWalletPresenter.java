package io.muun.apollo.presentation.ui.settings.success_delete_wallet;

import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.Optional;

import android.os.Bundle;
import androidx.annotation.Nullable;
import icepick.State;
import kotlin.Unit;

import javax.inject.Inject;

@PerActivity
public class SuccessDeleteWalletPresenter extends BasePresenter<SuccessDeleteWalletView> {

    @State
    @Nullable
    String supportId;

    @Inject
    public SuccessDeleteWalletPresenter() {

    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_FEEDBACK(AnalyticsEvent.FEEDBACK_TYPE.DELETE_WALLET);
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);
        supportId = arguments.getString(SuccessDeleteWalletView.SUPPORT_ID);
    }

    /**
     * Navigate to Feedback screen.
     */
    public Unit navigateToFeedback(String linkId) {
        navigator.navigateToSendGenericFeedback(getContext(), Optional.ofNullable(supportId));
        return null;
    }

    /**
     * Restart the application.
     */
    public void navigateToLauncher() {
        navigator.navigateToLauncher(getContext());
    }

    @Override
    protected boolean shouldCheckClientState() {
        return false;
    }
}