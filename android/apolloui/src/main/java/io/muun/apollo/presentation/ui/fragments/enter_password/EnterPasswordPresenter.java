package io.muun.apollo.presentation.ui.fragments.enter_password;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.analytics.AnalyticsEvent.PASSWORD_ERROR;
import io.muun.apollo.domain.errors.EmptyFieldError;
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError;
import io.muun.apollo.domain.errors.passwd.IncorrectPasswordError;
import io.muun.apollo.domain.model.auth.LoginOk;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;

import android.os.Bundle;
import android.text.TextUtils;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerFragment
public class EnterPasswordPresenter extends
        SingleFragmentPresenter<EnterPasswordView, EnterPasswordParentPresenter> {

    /**
     * Creates a presenter.
     */
    @Inject
    public EnterPasswordPresenter() {
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        view.setForgotPasswordVisible(getParentPresenter().canUseRecoveryCodeToLogin());
        setUpSubmitPassword();

        if (getParentPresenter().canUseRecoveryCodeToLogin()) {
            view.setForgotPasswordVisible(true);
        }
    }

    private void setUpSubmitPassword() {
        final Observable<ActionState<LoginOk>> observable = getParentPresenter()
                .watchSubmitEnterPassword()
                .doOnNext(state -> {
                    view.setLoading(state.isLoading());

                    if (state.isError()) {
                        handleError(state.getError());
                    }
                });

        subscribeTo(observable);
    }

    /**
     * Unlocks the account and takes the user to sync screen.
     */
    public void submitPassword(String password) {
        view.setPasswordError(null);

        if (isEmptyPassword(password)) {
            view.setPasswordError(new EmptyFieldError(EmptyFieldError.Field.PASSWORD));
            return;
        }

        getParentPresenter().submitEnterPassword(password);
    }

    @Override
    public void handleError(Throwable error) {
        if (error instanceof InvalidChallengeSignatureError) {
            view.setPasswordError(new IncorrectPasswordError());
            view.setReminderVisible(true);

            analytics.report(new AnalyticsEvent.E_PASSWORD(PASSWORD_ERROR.INCORRECT));

        } else {
            super.handleError(error);
        }
    }

    /**
     * Navigate to Forgot Password screen (aka Login with Recovery Code).
     */
    public void navigateToForgotPassword() {
        getParentPresenter().useRecoveryCodeToLogin();
    }

    boolean isEmptyPassword(String input) {
        return TextUtils.isEmpty(input);
    }

    /**
     * Abort sign up flow and return to landing.
     */
    public void abortSignIn() {
        getParentPresenter().cancelEnterPassword();
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_SIGN_IN_PASSWORD();
    }
}
