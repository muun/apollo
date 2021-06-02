package io.muun.apollo.presentation.ui.setup_password

import android.os.Bundle
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.challenge_keys.password_setup.SetUpPasswordAction
import io.muun.apollo.domain.action.challenge_keys.password_setup.StartEmailSetupAction
import io.muun.apollo.domain.errors.EmailAlreadyUsedError
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.fragments.create_password.CreatePasswordParentPresenter
import io.muun.apollo.presentation.ui.fragments.enter_email.CreateEmailParentPresenter
import io.muun.apollo.presentation.ui.fragments.password_setup_intro.SetupPasswordIntroParentPresenter
import io.muun.apollo.presentation.ui.fragments.setup_password_accept.SetupPasswordAcceptParentPresenter
import io.muun.apollo.presentation.ui.fragments.setup_password_success.SetupPasswordSuccessParentPresenter
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailParentPresenter
import rx.Observable
import javax.inject.Inject

class SetupPasswordActivityPresenter @Inject constructor(
    private val startEmailSetup: StartEmailSetupAction,
    private val setUpPassword: SetUpPasswordAction

): BasePresenter<SetupPasswordActivityView>(),
   SetupPasswordIntroParentPresenter,
   CreateEmailParentPresenter,
   VerifyEmailParentPresenter,
   CreatePasswordParentPresenter,
   SetupPasswordAcceptParentPresenter,
   SetupPasswordSuccessParentPresenter {

    private var form = SetupPasswordForm(
        step = SetupPasswordStep.INTRO,
        email = null,
        password = null
    )

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        startEmailSetup.state
            .compose(handleStates(null, this::handleError))
            .doOnNext {
                updateForm(form.copy(step = SetupPasswordStep.VERIFY_EMAIL))
            }
            .let(this::subscribeTo)

        userSel.watch()
            .filter { it.isEmailVerified }
            .doOnNext {
                if (form.step == SetupPasswordStep.VERIFY_EMAIL) {
                    updateForm(form.copy(step = SetupPasswordStep.CREATE_PASSWORD))
                }
            }
            .let(this::subscribeTo)

        setUpPassword.state
            .compose(handleStates(null, this::handleError))
            .doOnNext {
                updateForm(form.copy(step = SetupPasswordStep.SUCCESS))
            }
            .let(this::subscribeTo)
    }

    override fun startPasswordSetup() {
        updateForm(form.copy(step = SetupPasswordStep.CREATE_EMAIL))
    }

    override fun cancelIntro() {
        view.finishActivity()
    }

    override fun refreshToolbarTitle() {
        view.setUser(userSel.get())
    }

    override fun submitEmail(email: String) {
        updateForm(form.copy(email = email))
        startEmailSetup.run(email)
    }

    override fun watchSubmitEmail(): Observable<ActionState<Void>> =
        startEmailSetup.state

    override fun cancelCreateEmail() {
        updateForm(form.copy(step = SetupPasswordStep.INTRO))
    }

    override fun skipCreateEmail() {
        view.showSkipDialog()
    }

    override fun getEmail() =
        form.email

    override fun cancelVerifyEmail() {
        updateForm(form.copy(step = SetupPasswordStep.CREATE_EMAIL))
    }

    override fun submitPassword(password: String) {
        updateForm(form.copy(step = SetupPasswordStep.ACCEPT_TERMS, password = password))
    }

    override fun cancelCreatePassword() {
        view.showAbortDialog()
    }

    override fun acceptPasswordSetupTerms() {
        setUpPassword.run(form.password)
    }

    override fun watchAcceptPasswordSetupTerms(): Observable<ActionState<Void>> =
        setUpPassword.state

    override fun cancelAcceptTerms() {
        view.showAbortDialog()
    }

    fun abortPasswordSetup() {
        analytics.report(AnalyticsEvent.E_EMAIL_SETUP_ABORTED())
        view.finishActivity()
    }

    fun skipPasswordSetup() {
        analytics.report(AnalyticsEvent.E_EMAIL_SETUP_SKIPPED())
        userSel.skipEmailSetup()
        view.finishActivity()
    }

    override fun finishPasswordSetup() {
        analytics.report(AnalyticsEvent.E_EMAIL_SETUP_SUCCESSFUL())
        view.finishActivity()
    }

    private fun updateForm(newForm: SetupPasswordForm) {
        if (newForm.step != form.step) {
            view.goToStep(newForm.step)
        }

        form = newForm
    }

    override fun handleError(error: Throwable?) {
        when (error) {
            is EmailAlreadyUsedError -> {}  // Handled by children, don't intervene

            else -> return super.handleError(error)
        }
    }
}