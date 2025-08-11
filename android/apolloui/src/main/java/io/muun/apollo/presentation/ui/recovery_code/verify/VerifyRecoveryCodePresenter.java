package io.muun.apollo.presentation.ui.recovery_code.verify;


import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.analytics.AnalyticsEvent.RC_ERROR;
import io.muun.apollo.domain.errors.rc.InvalidCharacterRecoveryCodeError;
import io.muun.apollo.domain.errors.rc.RecoveryCodeVerificationError;
import io.muun.apollo.domain.libwallet.RecoveryCodeV2;
import io.muun.apollo.domain.libwallet.errors.InvalidRecoveryCodeFormatError;
import io.muun.apollo.domain.model.RecoveryCode;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter;
import io.muun.apollo.presentation.ui.recovery_code.accept.AcceptRecoveryCodeFragment;

import android.os.Bundle;

import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class VerifyRecoveryCodePresenter
        extends SingleFragmentPresenter<VerifyRecoveryCodeView, SetupRecoveryCodePresenter> {

    @Inject
    public VerifyRecoveryCodePresenter() {
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        final RecoveryCodeV2 recoveryCode = getParentPresenter().getRecoveryCode();

        // We now required ALL segments to be verified (code could be simpler but left as is to
        // remain flexible if we want to change current logic)
        final List<Integer> segmentsToVerify = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);

        view.setRecoveryCode(recoveryCode);
        view.setSegmentsToVerify(segmentsToVerify);
    }

    /**
     * Call when the user has accepted the displayed recovery code.
     */
    public void onRecoveryCodeEdited(String recoveryCodeString) {
        final RecoveryCodeV2 expectedRecoveryCode = getParentPresenter().getRecoveryCode();
        final RecoveryCodeV2 recoveryCode;

        view.setVerificationError(null);
        view.setConfirmEnabled(false);

        try {
            recoveryCode = RecoveryCodeV2.fromString(recoveryCodeString);

        } catch (RecoveryCode.RecoveryCodeLengthError error) {
            return; // ignore this error, the user is still typing.

        } catch (RecoveryCode.RecoveryCodeAlphabetError | InvalidRecoveryCodeFormatError error) {
            view.setVerificationError(new InvalidCharacterRecoveryCodeError());
            return;
        }

        if (! (recoveryCode.equals(expectedRecoveryCode) || Globals.INSTANCE.isDebug())) {
            // For Debug builds any code passes the verification check (easy testing)
            analytics.report(new AnalyticsEvent.E_RECOVERY_CODE(RC_ERROR.DID_NOT_MATCH));
            view.setVerificationError(new RecoveryCodeVerificationError());
            return;
        }

        view.setConfirmEnabled(true);
    }

    /**
     * Proceed to Accept Recovery Code screen after successful confirmation.
     */
    public void onRecoveryCodeConfirmed() {
        view.replaceFragment(new AcceptRecoveryCodeFragment(), false);
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_SET_UP_RECOVERY_CODE_VERIFY();
    }
}
