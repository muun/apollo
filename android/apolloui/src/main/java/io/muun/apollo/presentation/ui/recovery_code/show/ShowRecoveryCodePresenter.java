package io.muun.apollo.presentation.ui.recovery_code.show;


import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter;
import io.muun.apollo.presentation.ui.recovery_code.verify.VerifyRecoveryCodeFragment;

import android.os.Bundle;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerFragment
public class ShowRecoveryCodePresenter
        extends SingleFragmentPresenter<ShowRecoveryCodeView, SetupRecoveryCodePresenter> {

    private final UserRepository userRepository;

    @Inject
    public ShowRecoveryCodePresenter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        userRepository.setRecoveryCodeSetupInProcess(true);
        view.setRecoveryCode(getParentPresenter().getRecoveryCode());
    }

    /**
     * Call when the user has accepted the displayed recovery code.
     */
    public void continueToVerification() {
        view.replaceFragment(new VerifyRecoveryCodeFragment(), true);
    }

    public void showAbortDialog() {
        getParentPresenter().showAbortDialog();
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_SET_UP_RECOVERY_CODE_GENERATE();
    }
}
