package io.muun.apollo.presentation.ui.fragments.login_authorize;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.action.session.UseMuunLinkAction;
import io.muun.apollo.domain.action.user.EmailLinkAction;
import io.muun.apollo.domain.errors.ExpiredActionLinkError;
import io.muun.apollo.domain.errors.InvalidActionLinkError;
import io.muun.apollo.domain.selector.LoginAuthorizedSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailView;
import io.muun.apollo.presentation.ui.utils.UiNotificationPoller;
import io.muun.common.model.SessionStatus;

import android.os.Bundle;
import androidx.annotation.Nullable;
import rx.Observable;
import rx.functions.Action1;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

@PerFragment
public class LoginAuthorizePresenter extends
        SingleFragmentPresenter<VerifyEmailView, LoginAuthorizeParentPresenter> {

    private final UiNotificationPoller notificationPoller;
    private final LoginAuthorizedSelector loginAuthorizedSel;
    private final UseMuunLinkAction useMuunLinkAction;
    private final EmailLinkAction emailLinkAction;

    /**
     * Creates a presenter.
     */
    @Inject
    public LoginAuthorizePresenter(UiNotificationPoller notificationPoller,
                                   LoginAuthorizedSelector loginAuthorizedSel,
                                   UseMuunLinkAction useMuunLinkAction,
                                   EmailLinkAction emailLinkAction) {

        this.notificationPoller = notificationPoller;
        this.loginAuthorizedSel = loginAuthorizedSel;
        this.useMuunLinkAction = useMuunLinkAction;
        this.emailLinkAction = emailLinkAction;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        emailLinkAction.setPending(Globals.INSTANCE.getAuthorizeLinkPath());

        watchForEmailLinkErrors();
        watchForAuthorizeSignin();
        notificationPoller.start();

        view.setEmail(getParentPresenter().getSignupDraft().getEmail());
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

    private void watchForAuthorizeSignin() {
        final Observable<?> observable = loginAuthorizedSel.watch(SessionStatus.AUTHORIZED_BY_EMAIL)
                .filter(isAuthorized -> isAuthorized)
                .doOnNext(it -> getParentPresenter().reportEmailVerified());

        subscribeTo(observable);
    }

    public void onOpenEmailClient() {
        navigator.navigateToEmailClient(getContext());
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_AUTHORIZE_EMAIL();
    }

    public void goBack() {
        getParentPresenter().cancelEmailVerification();
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
