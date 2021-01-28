package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.action.challenge_keys.password_change.StartPasswordChangeAction;
import io.muun.apollo.domain.errors.IncorrectPasswordError;
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError;
import io.muun.apollo.domain.model.ChangePasswordStep;
import io.muun.apollo.domain.model.PendingChallengeUpdate;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.fragments.need_recovery_code.NeedRecoveryCodeFragment.Flow;
import io.muun.apollo.presentation.ui.settings.edit_password.BaseEditPasswordPresenter;
import io.muun.common.crypto.ChallengeType;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;

@PerFragment
public class OldPasswordPresenter extends BaseEditPasswordPresenter<OldPasswordView> {

    private final UserSelector userSelector;
    private final StartPasswordChangeAction startPasswordChange;

    /**
     * Creates a presenter.
     */
    @Inject
    public OldPasswordPresenter(UserSelector userSelector,
                                StartPasswordChangeAction startPasswordChange) {
        this.userSelector = userSelector;
        this.startPasswordChange = startPasswordChange;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        setUpBeginPasswordChangeAction();
    }

    private void setUpBeginPasswordChangeAction() {
        final Observable<ActionState<PendingChallengeUpdate>> observable = startPasswordChange
                .getState()
                .doOnNext(state -> {
                    switch (state.getKind()) {

                        case VALUE:
                            getParentPresenter().currentUuid = state.getValue().uuid;
                            navigateToStep(ChangePasswordStep.WAIT_FOR_EMAIL);
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

    @Override
    public void handleError(Throwable error) {
        if (error instanceof InvalidChallengeSignatureError) {
            view.setPasswordError(new IncorrectPasswordError());

        } else {
            super.handleError(error);
        }
    }

    /**
     * Continue change password flow using recovery code.
     */
    public void useRecoveryCode() {
        if (userSelector.get().hasRecoveryCode) {
            navigateToStep(ChangePasswordStep.EXISTING_RECOVERY_CODE);
        } else {
            navigator.navigateToMissingRecoveryCode(getContext(), Flow.CHANGE_PASSWORD);
        }
    }

    public void submitPassword(String password) {
        startPasswordChange.run(password, ChallengeType.PASSWORD);
    }
}
