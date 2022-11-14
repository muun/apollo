package io.muun.apollo.presentation.ui.recovery_code.show

import android.os.Bundle
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.SetRecoveryCodeSetupInProcessAction
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_SET_UP_RECOVERY_CODE_GENERATE
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter
import io.muun.apollo.presentation.ui.recovery_code.verify.VerifyRecoveryCodeFragment
import javax.inject.Inject
import javax.validation.constraints.NotNull

@PerFragment
internal class ShowRecoveryCodePresenter @Inject constructor(
    private val setRecoveryCodeSetupInProcess: SetRecoveryCodeSetupInProcessAction,
) : SingleFragmentPresenter<ShowRecoveryCodeView, SetupRecoveryCodePresenter>() {

    override fun setUp(@NotNull arguments: Bundle) {
        super.setUp(arguments)
        setRecoveryCodeSetupInProcess.run(true)
        view.setRecoveryCode(parentPresenter.recoveryCode)
    }

    /**
     * Call when the user has accepted the displayed recovery code.
     */
    fun continueToVerification() {
        view.replaceFragment(VerifyRecoveryCodeFragment(), true)
    }

    fun showAbortDialog() {
        parentPresenter.showAbortDialog()
    }

    override fun getEntryEvent(): AnalyticsEvent =
        S_SET_UP_RECOVERY_CODE_GENERATE()
}