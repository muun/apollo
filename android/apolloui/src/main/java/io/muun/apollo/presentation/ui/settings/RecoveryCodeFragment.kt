package io.muun.apollo.presentation.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.databinding.RecoveryCodeFragmentBinding
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment

class RecoveryCodeFragment : SingleFragment<RecoveryCodePresenter>(), RecoveryCodeView {

    private val binding: RecoveryCodeFragmentBinding
        get() = getBinding() as RecoveryCodeFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        RecoveryCodeFragmentBinding::inflate

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.enterRecoveryCodeBox.setEditable(true)
        binding.enterRecoveryCodeBox.setOnEditedListener { recoveryCodeString: String ->
            onRecoveryCodeEdited(recoveryCodeString)
        }
        binding.enterRecoveryCodeBox.requestFocusOnFirstEditableSegment()
        binding.enterRecoveryCodeBox.setOnKeyboardNextListeners()
        binding.enterRecoveryCodeBox.setOnKeyboardDoneListener {
            if (binding.enterRecoveryCodeContinue.isEnabled) {
                binding.enterRecoveryCodeContinue.callOnClick()
            }
        }

        binding.usePassword.setOnClickListener { onUsePasswordButtonClick() }
        binding.enterRecoveryCodeContinue.setOnClickListener { onContinueButtonClick() }
    }

    override fun onResume() {
        super.onResume()
        onRecoveryCodeEdited(binding.enterRecoveryCodeBox.segmentInputsContent)
        binding.enterRecoveryCodeBox.requestFocusOnFirstEditableSegment()
    }

    override fun blockScreenshots(): Boolean {
        return true
    }

    private fun onUsePasswordButtonClick() {
        parentActivity.onBackPressed()
    }

    private fun onContinueButtonClick() {
        presenter.submitRecoveryCode(binding.enterRecoveryCodeBox.segmentInputsContent)
    }

    override fun setRecoveryCodeError(error: UserFacingError?) {
        binding.enterRecoveryCodeBox.setError(error)
    }

    override fun setConfirmEnabled(isEnabled: Boolean) {
        binding.enterRecoveryCodeContinue.isEnabled = isEnabled
    }

    private fun onRecoveryCodeEdited(recoveryCodeString: String) {
        presenter.onRecoveryCodeEdited(recoveryCodeString)
    }
}
