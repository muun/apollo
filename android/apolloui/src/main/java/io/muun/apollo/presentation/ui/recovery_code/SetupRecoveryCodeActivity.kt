package io.muun.apollo.presentation.ui.recovery_code

import android.content.Context
import android.content.Intent
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.BaseFragment
import io.muun.apollo.presentation.ui.base.Presenter
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragmentDelegate
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel
import io.muun.apollo.presentation.ui.recovery_code.priming.PrimingRecoveryCodeFragment
import io.muun.apollo.presentation.ui.view.MuunHeader

internal class SetupRecoveryCodeActivity : SingleFragmentActivity<SetupRecoveryCodePresenter>(),
    SetupRecoveryCodeView,
    ErrorFragmentDelegate {

    companion object {
        const val SET_UP_RC_STEP_COUNT = 3

        /**
         * Creates an intent to launch this activity.
         */
        @JvmStatic
        fun getStartActivityIntent(context: Context): Intent {
            return Intent(context, SetupRecoveryCodeActivity::class.java)
        }
    }

    @BindView(R.id.recovery_code_header)
    lateinit var muunHeader: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.recovery_code_activity

    override fun getFragmentsContainer(): Int =
        R.id.fragment_container

    override fun getHeader(): MuunHeader =
        muunHeader

    override fun getInitialFragment(): BaseFragment<out Presenter<*>> =
        PrimingRecoveryCodeFragment()

    override fun initializeUi() {
        super.initializeUi()
        muunHeader.attachToActivity(this)
        muunHeader.setNavigation(MuunHeader.Navigation.EXIT)
    }

    override fun setUser(user: User) {
        if (user.hasPassword) {
            muunHeader.showTitle(R.string.security_center_title_improve_your_security)
        } else {
            muunHeader.showTitle(R.string.security_center_title_backup_your_wallet)
        }
    }

    override fun showAbortDialog() {
        val muunDialog = MuunDialog.Builder()
            .title(R.string.recovery_code_abort_title)
            .message(R.string.recovery_code_abort_body)
            .positiveButton(R.string.abort) { presenter.onSetupAborted() }
            .negativeButton(R.string.cancel, null)
            .build()
        showDialog(muunDialog)
    }

    override fun handleStartRecoveryCodeSetupConnectionError() {
        showError(
            ErrorViewModel.Builder()
                .loggingName(AnalyticsEvent.ERROR_TYPE.RC_SETUP_START_CONNECTION_ERROR)
                .kind(ErrorViewModel.ErrorViewKind.RETRYABLE)
                .title(getString(R.string.rc_setup_start_connection_error_title))
                .descriptionRes(R.string.rc_setup_start_connection_error_desc)
                .canGoBack(true)
                .build()
        )
    }

    override fun handleFinishRecoveryCodeSetupConnectionError() {
        showError(
            ErrorViewModel.Builder()
                .loggingName(AnalyticsEvent.ERROR_TYPE.RC_SETUP_FINISH_CONNECTION_ERROR)
                .kind(ErrorViewModel.ErrorViewKind.RETRYABLE)
                .title(getString(R.string.rc_setup_finish_connection_error_title))
                .descriptionRes(R.string.rc_setup_finish_connection_error_desc)
                .canGoBack(true)
                .build()
        )
    }

    override fun handleRetry(errorType: AnalyticsEvent.ERROR_TYPE) {
        hideError()
        if (errorType == AnalyticsEvent.ERROR_TYPE.RC_SETUP_START_CONNECTION_ERROR) {
            presenter.retryRecoveryCodeSetupStart()

        } else if (errorType == AnalyticsEvent.ERROR_TYPE.RC_SETUP_FINISH_CONNECTION_ERROR) {
            presenter.retryRecoveryCodeSetupFinish()
        }
    }

    override fun handleBack(errorType: AnalyticsEvent.ERROR_TYPE) {
        hideError()
        if (errorType == AnalyticsEvent.ERROR_TYPE.RC_SETUP_START_CONNECTION_ERROR) {
            presenter.handleBackFromErrorInStart()

        } else if (errorType == AnalyticsEvent.ERROR_TYPE.RC_SETUP_FINISH_CONNECTION_ERROR) {
            presenter.handleBackFromErrorInFinish()
        }
    }
}