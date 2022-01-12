package io.muun.apollo.presentation.ui.fragments.rc_only_login_auth

import android.os.Bundle
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.session.UseMuunLinkAction
import io.muun.apollo.domain.action.user.EmailLinkAction
import io.muun.apollo.domain.errors.ExpiredActionLinkError
import io.muun.apollo.domain.errors.InvalidActionLinkError
import io.muun.apollo.domain.selector.LoginAuthorizedSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailView
import io.muun.apollo.presentation.ui.utils.UiNotificationPoller
import io.muun.common.model.SessionStatus
import rx.Observable
import rx.functions.Action1
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RcLoginEmailAuthorizePresenter @Inject constructor(
    private val notificationPoller: UiNotificationPoller,
    private val useMuunLinkAction: UseMuunLinkAction,
    private val emailLinkAction: EmailLinkAction,
    private val loginAuthorizedSelector: LoginAuthorizedSelector
) : SingleFragmentPresenter<VerifyEmailView, RcLoginEmailAuthorizeParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        emailLinkAction.setPending(Globals.INSTANCE.rcLoginAuthorizePath)
        watchForEmailLinkErrors()
        watchForAuthorizeSignin()

        notificationPoller.start()

        // Show ofuscated email in message, we'll store it in and get from signUpDraft
        view.setEmail(parentPresenter.getOfuscatedEmail())
    }

    override fun tearDown() {
        super.tearDown()
        notificationPoller.stop()
    }

    private fun watchForEmailLinkErrors() {
        val observable = useMuunLinkAction.state
            .compose(handleStates(view::setLoading, this::handleError))
            .doOnNext {
                // This is a hackish attempt to handle Houston's ActionLinkAlreadyUsedException
                // which is currently returned as a 200 OK, empty response (like a successful
                // request). "Why?", you ask. GO FIGURE (seems like retrocompat blablity).
                // Bear in mind that if we are in a success scenario, we will probably continue
                // to next step (due to notification poller) before timer/loading finishes.
                Observable.timer(3, TimeUnit.SECONDS)
                    .compose(getAsyncExecutor())
                    .doOnNext { view.handleInvalidLinkError() }
                    .let(this::subscribeTo)
            }

        subscribeTo(observable)
    }

    private fun watchForAuthorizeSignin() {
        val observable = loginAuthorizedSelector.watch(SessionStatus.LOGGED_IN)
            .filter { isAuthorized: Boolean -> isAuthorized }
            .doOnNext {
                parentPresenter.reportRcLoginEmailVerified()
            }

        subscribeTo(observable)
    }

    fun goBack() {
        parentPresenter.cancelRcLoginEmailAuth()
    }

    fun openEmailClient() {
        navigator.navigateToEmailClient(context)
    }

    override fun handleError(error: Throwable?) {
        when (error) {
            is InvalidActionLinkError -> view.handleInvalidLinkError()
            is ExpiredActionLinkError -> view.handleExpiredLinkError()

            else -> return super.handleError(error)
        }
    }

    /**
     * Custom ActionState handling to allow loading until an external caller decides it.
     */
    override fun <T> handleStates(
        handleLoading: Action1<Boolean>?,
        handleError: Action1<Throwable>?
    ): Observable.Transformer<ActionState<T>, T> {

        return Observable.Transformer { observable: Observable<ActionState<T>> ->
            observable
                .doOnNext { state: ActionState<T> ->
                    if (handleLoading != null && state.isLoading) {
                        handleLoading.call(true)
                    }
                    if (handleError != null && state.isError) {
                        handleError.call(state.error)
                    }
                }
                .filter { obj: ActionState<T> -> obj.isValue }
                .map { obj: ActionState<T> -> obj.value }
        }
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_SIGN_IN_WITH_RC_AUTHORIZE_EMAIL()
    }
}
