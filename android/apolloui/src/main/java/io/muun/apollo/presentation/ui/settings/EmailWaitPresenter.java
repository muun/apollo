package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.action.session.UseMuunLinkAction;
import io.muun.apollo.domain.action.user.EmailLinkAction;
import io.muun.apollo.domain.errors.ExpiredActionLinkError;
import io.muun.apollo.domain.errors.InvalidActionLinkError;
import io.muun.apollo.domain.model.ChangePasswordStep;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailView;
import io.muun.apollo.presentation.ui.settings.edit_password.BaseEditPasswordPresenter;
import io.muun.apollo.presentation.ui.utils.UiNotificationPoller;
import io.muun.common.utils.Preconditions;

import android.os.Bundle;
import androidx.annotation.Nullable;
import rx.Observable;
import rx.functions.Action1;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

@PerFragment
public class EmailWaitPresenter extends BaseEditPasswordPresenter<VerifyEmailView> {

    private final UserActions userActions;
    private final UiNotificationPoller notificationPoller;
    private final UseMuunLinkAction useMuunLinkAction;
    private final EmailLinkAction emailLinkAction;

    /**
     * Creates a presenter.
     */
    @Inject
    public EmailWaitPresenter(UserActions userActions,
                              UiNotificationPoller notificationPoller,
                              UseMuunLinkAction useMuunLinkAction,
                              EmailLinkAction emailLinkAction) {

        this.userActions = userActions;
        this.notificationPoller = notificationPoller;
        this.useMuunLinkAction = useMuunLinkAction;
        this.emailLinkAction = emailLinkAction;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        emailLinkAction.setPending(Globals.INSTANCE.getConfirmLinkPath());

        watchForEmailLinkErrors();
        watchForEmailAuthorization();
        notificationPoller.start();

        // Only users with email + passwd set can CHANGE passwd (email is required to set up passwd)
        Preconditions.checkArgument(userSel.get().email.isPresent());

        view.setEmail(userSel.get().email.get());
    }

    @Override
    public void tearDown() {
        super.tearDown();
        notificationPoller.stop();
    }

    private void watchForEmailLinkErrors() {
        final Observable<?> observable = useMuunLinkAction
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext(ignored -> {
                    // This is a hackish attempt to handle Houston's ActionLinkAlreadyUsedException
                    // which is currently returned as a 200 OK, empty response (like a successful
                    // request). "Why?", you ask. GO FIGURE (seems like retrocompat blablity).
                    // Bear in mind that if we are in a success scenario, we will probably continue
                    // to next step (due to notification poller) before timer/loading finishes.
                    final Observable<Long> timerObservable = Observable.timer(3, TimeUnit.SECONDS)
                            .compose(getAsyncExecutor())
                            .doOnNext(someValue -> view.handleInvalidLinkError());

                    subscribeTo(timerObservable);
                });

        subscribeTo(observable);
    }

    private void watchForEmailAuthorization() {
        final Observable<String> observable = userActions.awaitPasswordChangeEmailAuthorization()
                .compose(transformerFactory.getAsyncExecutor())
                .doOnNext(this::handleAuthorization);

        subscribeTo(observable);
    }

    private void handleAuthorization(String authorizedUuid) {
        if (authorizedUuid.equals(getParentPresenter().currentUuid)) {
            navigateToStep(ChangePasswordStep.NEW_PASSWORD);
        }
    }

    public void onOpenEmailClient() {
        navigator.navigateToEmailClient(getContext());
    }

    @Override
    public void handleError(Throwable error) {
        if (error instanceof InvalidActionLinkError) {
            view.handleInvalidLinkError();

        } else if (error instanceof ExpiredActionLinkError) {
            view.handleExpiredLinkError();

        } else {
            super.handleError(error);
        }
    }

    /**
     * Custom ActionState handling to allow loading until an external caller decides it.
     */
    protected <T> Observable.Transformer<ActionState<T>, T> handleStates(
            @Nullable Action1<Boolean> handleLoading,
            @Nullable Action1<Throwable> handleError) {

        return observable -> observable
                .doOnNext(state -> {
                    if (handleLoading != null && state.isLoading()) {
                        handleLoading.call(true);
                    }

                    if (handleError != null && state.isError()) {
                        handleError.call(state.getError());
                    }
                })
                .filter(ActionState::isValue)
                .map(ActionState::getValue);
    }
}
