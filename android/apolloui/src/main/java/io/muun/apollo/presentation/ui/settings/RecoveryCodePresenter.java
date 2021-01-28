package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.action.challenge_keys.password_change.StartPasswordChangeAction;
import io.muun.apollo.domain.errors.IncorrectRecoveryCodeError;
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError;
import io.muun.apollo.domain.errors.InvalidCharacterRecoveryCodeError;
import io.muun.apollo.domain.libwallet.errors.InvalidRecoveryCodeFormatError;
import io.muun.apollo.domain.model.ChangePasswordStep;
import io.muun.apollo.domain.model.PendingChallengeUpdate;
import io.muun.apollo.domain.model.RecoveryCode;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.settings.edit_password.BaseEditPasswordPresenter;
import io.muun.common.crypto.ChallengeType;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;

@PerFragment
public class RecoveryCodePresenter extends BaseEditPasswordPresenter<RecoveryCodeView> {

    private final StartPasswordChangeAction startPasswordChange;

    /**
     * Creates a presenter.
     */
    @Inject
    public RecoveryCodePresenter(StartPasswordChangeAction startPasswordChange) {
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
            view.setRecoveryCodeError(new IncorrectRecoveryCodeError());
        } else {
            super.handleError(error);
        }
    }

    /**
     * Called when the user edits some part of the recovery code.
     */
    public void onRecoveryCodeEdited(String recoveryCodeString) {
        view.setRecoveryCodeError(null);
        view.setConfirmEnabled(false);

        try {
            RecoveryCode.validate(recoveryCodeString);
            view.setConfirmEnabled(true);

        } catch (RecoveryCode.RecoveryCodeAlphabetError | InvalidRecoveryCodeFormatError error) {
            view.setRecoveryCodeError(new InvalidCharacterRecoveryCodeError());

        } catch (RecoveryCode.RecoveryCodeLengthError error) {
            // Do nothing. Let the user finish typing.

        } catch (Exception error) {
            handleError(error);
        }
    }

    /**
     * Start passwordItem change process, using the user's recovery code.
     */
    public void submitRecoveryCode(String recoveryCode) {
        startPasswordChange.run(recoveryCode, ChallengeType.RECOVERY_CODE);
    }
}
