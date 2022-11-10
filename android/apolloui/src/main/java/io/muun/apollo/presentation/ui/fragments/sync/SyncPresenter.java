package io.muun.apollo.presentation.ui.fragments.sync;

import io.muun.apollo.domain.action.session.SyncApplicationDataAction;
import io.muun.apollo.domain.model.LoginWithRc;
import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.domain.model.SignupStep;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.signup.SignupPresenter;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Preconditions;

import android.Manifest;
import android.os.Bundle;
import icepick.State;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerFragment
public class SyncPresenter extends SingleFragmentPresenter<SyncView, SignupPresenter> {

    private final SyncApplicationDataAction syncApplicationData;

    @State
    boolean isSyncComplete;

    @State
    boolean isPinSetUp;

    /**
     * Creates a sync presenter.
     */
    @Inject
    public SyncPresenter(SyncApplicationDataAction syncApplicationData) {
        this.syncApplicationData = syncApplicationData;
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        setUpSyncApplicationDataAction();

        if (! isPinSetUp) {
            view.setUpPinCode(false);
        }

        final SignupDraft signupDraft = getParentPresenter().getSignupDraft();

        if (! isSyncComplete) {

            final boolean isFirstSession = !signupDraft.isExistingUser();
            final LoginWithRc loginWithRc = signupDraft.getLoginWithRc();
            syncApplicationData.run(isFirstSession, canReadContacts(), loginWithRc);
        }

        view.setIsExistingUser(signupDraft.isExistingUser());
    }

    /**
     * Signal PIN code setup ended successfully.
     */
    public void pinCodeSetUpSuccess() {
        isPinSetUp = true;
        finishSignupIfReady();
    }

    private void setUpSyncApplicationDataAction() {
        final Observable<?> observable = syncApplicationData
                .getState()
                .doOnNext(state -> {
                    switch (state.getKind()) {
                        case EMPTY:
                            view.setLoading(false);
                            break;

                        case LOADING:
                            view.setLoading(true);
                            break;

                        case VALUE:
                            isSyncComplete = true;
                            reportSyncComplete(
                                    getParentPresenter().getSignupDraft().isExistingUser()
                            );
                            finishSignupIfReady();
                            break;

                        case ERROR:
                            view.setLoading(false);
                            handleError(state.getError());
                            break;

                        default:
                            break;
                    }
                });

        subscribeTo(observable);
    }

    private boolean canReadContacts() {
        return hasPermission(Manifest.permission.READ_CONTACTS);
    }

    private void finishSignupIfReady() {
        if (isPinSetUp && isSyncComplete) {
            getParentPresenter().reportSyncComplete();
        }
    }

    private void reportSyncComplete(boolean isExistingUser) {
        if (isExistingUser) {
            analytics.report(new AnalyticsEvent.E_SIGN_IN_SUCCESSFUL(getLoginType()));
        } else {
            analytics.report(new AnalyticsEvent.E_WALLET_CREATED());
        }

        // If the RC used for wallet recovery was UNVERIFIED, we'll show a warning prompting the
        // user to finish their Security Center setup.
        // For the scenario where a user recovers their wallet using their RC, and has an email
        // associated, an email authorization link will be sent. Once the user clicks it, we allow
        // the wallet recovery to take place. In this scenario, this is where we perform the check
        // to decide if we need to show the warning.
        final LoginWithRc loginWithRc = getParentPresenter().getSignupDraft().getLoginWithRc();
        if (loginWithRc != null) {
            if (!userSel.get().hasRecoveryCode) {
                getParentPresenter().getSignupDraft().setShowUnverifiedRcWarning(true);
            }
        }
    }

    private AnalyticsEvent.LoginType getLoginType() {

        final SignupStep previousStep = getPreviousStep();

        switch (previousStep) {

            case LOGIN_PASSWORD:
                return AnalyticsEvent.LoginType.PASSWORD;

            case LOGIN_RECOVERY_CODE:
                return AnalyticsEvent.LoginType.EMAIL_AND_RECOVERY_CODE;

            case LOGIN_RECOVERY_CODE_ONLY:
                return AnalyticsEvent.LoginType.RECOVERY_CODE;

            case LOGIN_RECOVERY_CODE_EMAIL_AUTH:
                return AnalyticsEvent.LoginType.RECOVERY_CODE_AND_EMAIL;

            default:
                throw new MissingCaseError(previousStep);
        }
    }

    private SignupStep getPreviousStep() {
        final SignupStep previousStep = getParentPresenter().getSignupDraft().getPreviousStep();

        Preconditions.checkState(previousStep != null);

        return SignupStep.valueOf(previousStep.name());
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_SYNC();
    }
}
