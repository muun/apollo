package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.action.challenge_keys.password_change.FinishPasswordChangeAction;
import io.muun.apollo.domain.errors.EmptyFieldError;
import io.muun.apollo.domain.errors.PasswordTooShortError;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.common.Rules;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;

@PerFragment
public class ChangePasswordPresenter extends BaseEditPasswordPresenter<ChangePasswordView> {

    private final FinishPasswordChangeAction finishPasswordChange;

    /**
     * Creates a presenter.
     */
    @Inject
    public ChangePasswordPresenter(FinishPasswordChangeAction finishPasswordChange) {
        this.finishPasswordChange = finishPasswordChange;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        setUpFinishPasswordChangeAction();
    }

    private void setUpFinishPasswordChangeAction() {
        final Observable<ActionState<Void>> observable = finishPasswordChange
                .getState()
                .doOnNext(state -> {
                    switch (state.getKind()) {

                        case VALUE:
                            analytics.report(new AnalyticsEvent.E_PASSWORD_CHANGED());
                            getParentPresenter().onChangeSuccessful();
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

    /**
     * Submit the new password, checking for errors.
     */
    public void submitPassword(String password) {
        view.setPasswordError(null);

        if (password.equals("")) {
            view.setPasswordError(new EmptyFieldError(EmptyFieldError.Field.PASSWORD));

        } else if (password.length() < Rules.PASSWORD_MIN_LENGTH) {
            view.setPasswordError(new PasswordTooShortError());

        } else {
            view.setPasswordError(null);
            view.setLoading(true);
            finishPasswordChange.run(getParentPresenter().currentUuid, password);
        }
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_PASSWORD_CHANGE_END();
    }
}
