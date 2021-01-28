package io.muun.apollo.presentation.ui.recovery_code;


import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.libwallet.RecoveryCodeV2;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.bundler.RecoveryCodeBundler;
import io.muun.apollo.presentation.ui.recovery_code.show.ShowRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.recovery_code.success.SuccessRecoveryCodeFragment;

import android.os.Bundle;
import icepick.State;

import javax.inject.Inject;

@PerActivity
public class SetupRecoveryCodePresenter extends BasePresenter<SetupRecoveryCodeView> {

    private final UserRepository userRepository;

    @State(RecoveryCodeBundler.class)
    RecoveryCodeV2 recoveryCode;

    @Inject
    public SetupRecoveryCodePresenter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        // Create a RecoveryCode, unless we already did and @State preserved it:
        if (recoveryCode == null) {
            recoveryCode = RecoveryCodeV2.createRandom();
        }

        view.setUser(userSel.get());
    }

    public RecoveryCodeV2 getRecoveryCode() {
        return recoveryCode;
    }

    public void showAbortDialog() {
        view.showAbortDialog();
    }

    public void onSetupAborted() {
        userRepository.setRecoveryCodeSetupInProcess(false);
        view.finishActivity();
    }

    public void onSetupSuccessful() {
        analytics.report(new AnalyticsEvent.E_RECOVERY_CODE_SET_UP());
        view.replaceFragment(new SuccessRecoveryCodeFragment(), false);
    }

    public void goToRecoveryCode() {
        view.replaceFragment(new ShowRecoveryCodeFragment(), false);
    }
}
