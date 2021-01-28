package io.muun.apollo.presentation.ui.recovery_code.priming;

import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter;

import android.os.Bundle;

import javax.inject.Inject;

@PerFragment
public class PrimingRecoveryCodePresenter
        extends SingleFragmentPresenter<PrimingRecoveryCodeView, SetupRecoveryCodePresenter> {

    @Inject
    public PrimingRecoveryCodePresenter() {
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        view.setTexts(userSel.get());
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_RECOVERY_CODE_PRIMING();
    }

    /**
     * Call when the user has touched the start button.
     */
    public void continueToShowRecoveryCode() {
        getParentPresenter().goToRecoveryCode();
    }
}