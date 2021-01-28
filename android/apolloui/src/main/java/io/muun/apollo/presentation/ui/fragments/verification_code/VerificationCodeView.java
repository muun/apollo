package io.muun.apollo.presentation.ui.fragments.verification_code;


import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface VerificationCodeView extends SingleFragmentView {
    void setPhoneNumber(String phoneNumber);

    void setLoading(boolean isLoading);

    void handleVerificationCodeError(Throwable error);
}
