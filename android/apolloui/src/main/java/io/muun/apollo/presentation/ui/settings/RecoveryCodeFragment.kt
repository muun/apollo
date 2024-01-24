package io.muun.apollo.presentation.ui.settings

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunRecoveryCodeBox

class RecoveryCodeFragment : SingleFragment<RecoveryCodePresenter>(), RecoveryCodeView {

    @BindView(R.id.enter_recovery_code_box)
    lateinit var recoveryCodeBox: MuunRecoveryCodeBox

    @BindView(R.id.use_password)
    lateinit var usePasswordButton: MuunButton

    @BindView(R.id.enter_recovery_code_continue)
    lateinit var continueButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.recovery_code_fragment

    override fun initializeUi(view: View) {
        recoveryCodeBox.setEditable(true)
        recoveryCodeBox.setOnEditedListener { recoveryCodeString: String ->
            onRecoveryCodeEdited(recoveryCodeString)
        }
        recoveryCodeBox.requestFocusOnFirstEditableSegment()
        recoveryCodeBox.setOnKeyboardNextListeners()
        recoveryCodeBox.setOnKeyboardDoneListener {
            if (continueButton.isEnabled) {
                continueButton.callOnClick()
            }
        }

        usePasswordButton.setOnClickListener { onUsePasswordButtonClick() }
        continueButton.setOnClickListener { onContinueButtonClick() }
    }

    override fun onResume() {
        super.onResume()
        onRecoveryCodeEdited(recoveryCodeBox.segmentInputsContent) // re-trigger validation
        recoveryCodeBox.requestFocusOnFirstEditableSegment()
    }

    override fun blockScreenshots(): Boolean {
        return true
    }

    private fun onUsePasswordButtonClick() {
        parentActivity.onBackPressed()
    }

    private fun onContinueButtonClick() {
        presenter.submitRecoveryCode(recoveryCodeBox.segmentInputsContent)
    }

    override fun setRecoveryCodeError(error: UserFacingError?) {
        recoveryCodeBox.setError(error)
    }

    override fun setConfirmEnabled(isEnabled: Boolean) {
        continueButton.isEnabled = isEnabled
    }

    private fun onRecoveryCodeEdited(recoveryCodeString: String) {
        presenter.onRecoveryCodeEdited(recoveryCodeString)
    }
}