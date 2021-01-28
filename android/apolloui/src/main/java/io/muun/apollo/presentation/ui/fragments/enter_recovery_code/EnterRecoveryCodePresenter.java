package io.muun.apollo.presentation.ui.fragments.enter_recovery_code;

import io.muun.apollo.domain.errors.IncorrectRecoveryCodeError;
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError;
import io.muun.apollo.domain.errors.InvalidCharacterRecoveryCodeError;
import io.muun.apollo.domain.libwallet.errors.InvalidRecoveryCodeFormatError;
import io.muun.apollo.domain.model.RecoveryCode;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.settings.RecoveryCodeView;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;

@PerFragment
public class EnterRecoveryCodePresenter
        extends SingleFragmentPresenter<RecoveryCodeView, EnterRecoveryCodeParentPresenter> {

    /**
     * Creates a presenter.
     */
    @Inject
    public EnterRecoveryCodePresenter() {
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        setUpSubmitRecoveryCode();
    }

    private void setUpSubmitRecoveryCode() {
        final Observable<?> observable = getParentPresenter()
                .watchSubmitEnterRecoveryCode()
                .compose(handleStates(view::setLoading, this::handleError));

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

    public void goBack() {
        getParentPresenter().cancelEnterRecoveryCode();
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
     * Sign Houston's challenge with this recovery code and attempt to fetch keys.
     */
    public void submitRecoveryCode(String recoveryCode) {
        getParentPresenter().submitEnterRecoveryCode(recoveryCode);
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_INPUT_RECOVERY_CODE();
    }
}
