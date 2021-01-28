package io.muun.apollo.presentation.ui.fragments.verification_code;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.presentation.ui.base.ParentPresenter;
import io.muun.common.model.VerificationType;

import rx.Observable;

public interface VerificationCodeParentPresenter extends ParentPresenter {

    void submitVerificationCode(String verificationCode);

    Observable<ActionState<UserPhoneNumber>> watchSubmitVerificationCode();

    void changePhoneNumber();

    void resendVerificationCode(VerificationType verificationType);
}
