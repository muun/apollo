package io.muun.apollo.presentation.ui.fragments.login_email

import android.os.Bundle
import io.muun.apollo.domain.errors.EmailNotRegisteredError
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_SIGN_IN_EMAIL
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.common.bitcoinj.ValidationHelpers
import rx.Observable
import javax.inject.Inject

@PerFragment
class LoginEmailPresenter @Inject constructor():
    SingleFragmentPresenter<LoginEmailView, LoginEmailParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        // Fill email input if already present in draft:
        parentPresenter.signupDraft.email?.let(view::autoFillEmail)

        setUpSubmitEmail()
    }

    private fun setUpSubmitEmail() {
        val observable: Observable<*> = parentPresenter
            .watchSubmitEmail()
            .doOnNext { state ->
                view.setLoading(state.isLoading)

                if (state.isError) {
                    handleError(state.error)

                } else if (state.isValue && !state.value.isExistingUser) {
                    // We were expecting this email to belong to an existing user.
                    handleError(EmailNotRegisteredError())
                }
            }

        subscribeTo(observable)
    }

    /**
     * Submit the entered email to continue the parent flow.
     */
    fun submitEmail(email: String) {
        if (ValidationHelpers.isValidEmail(email)) {
            parentPresenter.submitEmail(email)
        }
    }

    override fun handleError(error: Throwable) {
        if (error is EmailNotRegisteredError) {
            view.setEmailError(error)

        } else {
            super.handleError(error)
        }
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return S_SIGN_IN_EMAIL()
    }

    fun goBack() {
        parentPresenter.cancelEnterEmail()
    }

    fun useRecoveryCodeOnlyLogin() {
        parentPresenter.useRecoveryCodeOnlyLogin()
    }
}