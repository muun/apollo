package io.muun.apollo.presentation.ui.recovery_code

import android.os.Bundle
import icepick.State
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.FinishRecoveryCodeSetupAction
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.SetRecoveryCodeSetupInProcessAction
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.StartRecoveryCodeSetupAction
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_RECOVERY_CODE_SET_UP
import io.muun.apollo.domain.errors.rc.FinishRecoveryCodeSetupError
import io.muun.apollo.domain.errors.rc.StartRecoveryCodeSetupError
import io.muun.apollo.domain.libwallet.RecoveryCodeV2
import io.muun.apollo.domain.libwallet.RecoveryCodeV2.Companion.createRandom
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByNetworkError
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.bundler.RecoveryCodeBundler
import io.muun.apollo.presentation.ui.recovery_code.show.ShowRecoveryCodeFragment
import io.muun.apollo.presentation.ui.recovery_code.success.SuccessRecoveryCodeFragment
import javax.inject.Inject

@PerActivity
internal class SetupRecoveryCodePresenter @Inject constructor(
    private val setRecoveryCodeSetupInProcess: SetRecoveryCodeSetupInProcessAction,
    private val startRecoveryCodeSetup: StartRecoveryCodeSetupAction,
    private val finishRecoveryCodeSetup: FinishRecoveryCodeSetupAction,
) : BasePresenter<SetupRecoveryCodeView>() {

    @State(RecoveryCodeBundler::class)
    lateinit var recoveryCode: RecoveryCodeV2

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        // Create a RecoveryCode, unless we already did and @State preserved it:
        if (!::recoveryCode.isInitialized) {
            recoveryCode = createRandom()
            // Start houston upload as soon as we can
            startRecoveryCodeSetup.run(recoveryCode)
        }

        view.setUser(userSel.get())
    }

    fun showAbortDialog() {
        view.showAbortDialog()
    }

    fun onSetupAborted() {
        setRecoveryCodeSetupInProcess.run(false)
        view.finishActivity()
    }

    fun onSetupSuccessful() {
        analytics.report(E_RECOVERY_CODE_SET_UP())
        view.replaceFragment(SuccessRecoveryCodeFragment(), false)
    }

    fun goToRecoveryCode() {
        view.replaceFragment(ShowRecoveryCodeFragment(), false)
    }

    override fun handleError(error: Throwable) {
        if (error.isInstanceOrIsCausedByNetworkError()) {
            when (error) {
                is StartRecoveryCodeSetupError -> {
                    view.handleStartRecoveryCodeSetupConnectionError()
                }
                is FinishRecoveryCodeSetupError -> {
                    view.handleFinishRecoveryCodeSetupConnectionError()
                }
                else -> {
                    // Shouldn't happen but still..
                    super.handleError(error)
                }
            }
        } else {
            super.handleError(error)
        }
    }

    fun retryRecoveryCodeSetupStart() {
        startRecoveryCodeSetup.reset()
        startRecoveryCodeSetup.run(recoveryCode)
    }

    fun retryRecoveryCodeSetupFinish() {
        finishRecoveryCodeSetup.reset()
        finishRecoveryCodeSetup.run()
    }

    fun handleBackFromErrorInStart() {
        startRecoveryCodeSetup.reset()
    }

    fun handleBackFromErrorInFinish() {
        finishRecoveryCodeSetup.reset()
    }
}