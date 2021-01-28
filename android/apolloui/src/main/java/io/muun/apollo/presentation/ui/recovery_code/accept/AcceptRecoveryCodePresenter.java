package io.muun.apollo.presentation.ui.recovery_code.accept;


import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.SetUpRecoveryCodeAction;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;

@PerFragment
public class AcceptRecoveryCodePresenter
        extends SingleFragmentPresenter<AcceptRecoveryCodeView, SetupRecoveryCodePresenter> {

    private final SetUpRecoveryCodeAction setUpRecoveryCode;

    @Inject
    public AcceptRecoveryCodePresenter(SetUpRecoveryCodeAction setUpRecoveryCode) {
        this.setUpRecoveryCode = setUpRecoveryCode;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        view.setTexts(userSel.get());

        setUpUpdateChallengeSetupAction();
    }

    private void setUpUpdateChallengeSetupAction() {
        final Observable<?> observable = setUpRecoveryCode
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext(ignored -> getParentPresenter().onSetupSuccessful());

        subscribeTo(observable);
    }

    /**
     * Send Houston our new challenge key.
     */
    public void finishSetup() {
        setUpRecoveryCode.run(getParentPresenter().getRecoveryCode().toString());
    }

    public void showAbortDialog() {
        getParentPresenter().showAbortDialog();
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_FINISH_RECOVERY_CODE_CONFIRM();
    }
}
