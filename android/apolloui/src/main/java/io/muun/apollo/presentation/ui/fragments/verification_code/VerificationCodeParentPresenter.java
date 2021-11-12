package io.muun.apollo.presentation.ui.fragments.verification_code;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.model.user.UserPhoneNumber;
import io.muun.apollo.presentation.ui.base.ParentPresenter;
import io.muun.common.model.VerificationType;

import rx.Observable;

public interface VerificationCodeParentPresenter extends ParentPresenter {

    /**
     * Submit a verificationCode to complete phone number verification.
     */
    void submitVerificationCode(String verificationCode);

    /**
     * Get an Observable for the SubmitVerificationCode async action.
     */
    Observable<ActionState<UserPhoneNumber>> watchSubmitVerificationCode();

    /**
     * Go back to previous step to input another phone number.
     */
    void changePhoneNumber();

    /**
     * Asks Houston for a new verification code, to be sent via sms or phone call.
     * @param verificationType the way to deliver the verification code, either sms or phone call
     */
    void resendVerificationCode(VerificationType verificationType);
}
