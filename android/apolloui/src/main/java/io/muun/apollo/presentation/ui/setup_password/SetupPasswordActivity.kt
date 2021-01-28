package io.muun.apollo.presentation.ui.setup_password

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.User
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.fragments.create_password.CreatePasswordFragment
import io.muun.apollo.presentation.ui.fragments.enter_email.CreateEmailFragment
import io.muun.apollo.presentation.ui.fragments.password_setup_intro.SetupPasswordIntroFragment
import io.muun.apollo.presentation.ui.fragments.setup_password_accept.SetupPasswordAcceptFragment
import io.muun.apollo.presentation.ui.fragments.setup_password_success.SetupPasswordSuccessFragment
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailFragment
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation

class SetupPasswordActivity: SingleFragmentActivity<SetupPasswordActivityPresenter>(),
                             SetupPasswordActivityView {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, SetupPasswordActivity::class.java)
    }

    @BindView(R.id.header)
    lateinit var headerView: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getFragmentsContainer() =
        R.id.container

    override fun getLayoutResource() =
        R.layout.activity_setup_password

    override fun isPresenterPersistent() =
        true

    override fun initializeUi() {
        super.initializeUi()

        headerView.let {
            it.attachToActivity(this)
            it.setNavigation(Navigation.BACK)
        }
    }

    override fun getHeader() =
        headerView

    override fun getInitialFragment() =
        SetupPasswordIntroFragment()

    override fun setUser(user: User) {
        if (user.hasRecoveryCode) {
            headerView.showTitle(R.string.security_center_title_improve_your_security)

        } else {
            headerView.showTitle(R.string.security_center_title_backup_your_wallet)
        }

        headerView.setElevated(true)
    }

    override fun showAbortDialog() {
        MuunDialog.Builder()
            .title(R.string.setup_password_abort_title)
            .message(R.string.setup_password_abort_body)
            .positiveButton(R.string.abort) { presenter.abortPasswordSetup() }
            .negativeButton(R.string.cancel, null)
            .build()
            .let(this::showDialog)
    }

    override fun showSkipDialog() {
        MuunDialog.Builder()
            .layout(R.layout.dialog_custom_layout)
            .title(R.string.setup_password_skip_title)
            .message(R.string.setup_password_skip_body)
            .positiveButton(R.string.setup_password_skip_yes) { presenter.skipPasswordSetup() }
            .negativeButton(R.string.setup_password_skip_no, null)
            .build()
            .let(this::showDialog)
    }

    @SuppressLint("StringFormatMatches")
    override fun goToStep(step: SetupPasswordStep) {
        val nextFragment = when (step) {
            SetupPasswordStep.INTRO -> SetupPasswordIntroFragment()
            SetupPasswordStep.CREATE_EMAIL -> CreateEmailFragment()
            SetupPasswordStep.VERIFY_EMAIL -> VerifyEmailFragment()
            SetupPasswordStep.CREATE_PASSWORD -> CreatePasswordFragment()
            SetupPasswordStep.ACCEPT_TERMS -> SetupPasswordAcceptFragment()
            SetupPasswordStep.SUCCESS -> SetupPasswordSuccessFragment()
        }

        val firstNumberedStep = SetupPasswordStep.CREATE_EMAIL.ordinal
        val lastNumberedStep = SetupPasswordStep.ACCEPT_TERMS.ordinal
        val stepNumber = step.ordinal

        val indicatorText = if (stepNumber in firstNumberedStep..lastNumberedStep) {
            getString(R.string.setup_password_step_counter, stepNumber, lastNumberedStep)
        } else {
            ""
        }

        headerView.setIndicatorText(indicatorText)

        val canGoBackToCurrentStep = (step != SetupPasswordStep.VERIFY_EMAIL)
        replaceFragment(nextFragment, canGoBackToCurrentStep)
    }
}