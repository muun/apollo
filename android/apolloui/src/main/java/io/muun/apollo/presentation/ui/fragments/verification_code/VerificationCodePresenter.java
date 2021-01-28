package io.muun.apollo.presentation.ui.fragments.verification_code;

import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.errors.InvalidVerificationCodeError;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.common.model.VerificationType;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerFragment
public class VerificationCodePresenter
        extends SingleFragmentPresenter<VerificationCodeView, VerificationCodeParentPresenter> {

    private final UserRepository userRepository;

    /**
     * Create a VerificationCodePresenter.
     */
    @Inject
    public VerificationCodePresenter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {

        super.setUp(arguments);

        setPhoneNumber();
        setupConfirmPhoneAction();
    }

    private void setPhoneNumber() {
        view.setPhoneNumber(userRepository.fetchOne().phoneNumber.get().toPrettyString());
    }

    private void setupConfirmPhoneAction() {
        final Observable<?> observable = getParentPresenter()
                .watchSubmitVerificationCode()
                .doOnNext(state -> {
                    view.setLoading(state.isLoading());

                    if (state.isError()) {
                        handleError(state.getError());
                    }
                });

        subscribeTo(observable);
    }

    /**
     * Asks for a new verification code to Houston via sms or phone call.
     * @param verificationType the way to deliver the verification code, either sms or phone call
     */
    public void resendVerification(VerificationType verificationType) {
        getParentPresenter().resendVerificationCode(verificationType);
    }

    /**
     * Verifies the phone number of a user with the received verification code.
     */
    public void submitVerificationCode(String verificationCode) {
        view.handleVerificationCodeError(null);
        getParentPresenter().submitVerificationCode(verificationCode);
    }

    public void changePhoneNumber() {
        getParentPresenter().changePhoneNumber();
    }

    @Override
    public void handleError(Throwable error) {
        if (error instanceof InvalidVerificationCodeError) {
            view.handleVerificationCodeError(error);
        }

        super.handleError(error);
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_P2P_SETUP_VERIFICATION_CODE();
    }
}
