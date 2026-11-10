package io.muun.apollo.presentation.ui.fragments.rc_only_login

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentRcOnlyLoginBinding
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.MuunHeader

class RcOnlyLoginFragment : SingleFragment<RcOnlyLoginPresenter>(), RcOnlyLoginView {

    private val binding: FragmentRcOnlyLoginBinding
        get() = getBinding() as FragmentRcOnlyLoginBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentRcOnlyLoginBinding::inflate
    }

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.rcOnlyLoginRecoveryCodeBox.setEditable(true)
        binding.rcOnlyLoginRecoveryCodeBox.setOnEditedListener(this::onRecoveryCodeEdited)
        binding.rcOnlyLoginRecoveryCodeBox.requestFocusOnFirstEditableSegment()
        binding.rcOnlyLoginRecoveryCodeBox.setOnKeyboardNextListeners()
        binding.rcOnlyLoginRecoveryCodeBox.setOnKeyboardDoneListener {
            if (binding.rcOnlyLoginContinue.isEnabled) {
                binding.rcOnlyLoginContinue.callOnClick()
            }
        }

        binding.rcOnlyLoginWhatsThis.text = getWhatThisText()
        binding.rcOnlyLoginContinue.setOnClickListener(this::onContinueClick)
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            setNavigation(MuunHeader.Navigation.BACK)
            showTitle(R.string.login_title)
            setElevated(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // re-trigger validation
        onRecoveryCodeEdited(binding.rcOnlyLoginRecoveryCodeBox.segmentInputsContent)
        binding.rcOnlyLoginRecoveryCodeBox.requestFocusOnFirstEditableSegment()
    }

    override fun blockScreenshots(): Boolean {
        return true
    }

    override fun setLoading(loading: Boolean) {
        binding.rcOnlyLoginContinue.setLoading(loading)
        binding.rcOnlyLoginRecoveryCodeBox.setEditable(!loading)
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    override fun setRecoveryCodeError(error: UserFacingError?) {
        binding.rcOnlyLoginRecoveryCodeBox.setError(error)
    }

    override fun handleLegacyRecoveryCodeError() {
        binding.rcOnlyLoginRecoveryCodeBox.showLegacyRecoveryCodeError { onBackPressed() }
    }

    override fun setConfirmEnabled(enabled: Boolean) {
        binding.rcOnlyLoginContinue.isEnabled = enabled
    }

    private fun onRecoveryCodeEdited(recoveryCode: String) {
        presenter.onRecoveryCodeEdited(recoveryCode)
    }

    private fun onContinueClick(view: View) {
        presenter.submitRecoveryCode(binding.rcOnlyLoginRecoveryCodeBox.segmentInputsContent)
    }

    private fun getWhatThisText() =
        StyledStringRes(requireContext(), R.string.rc_only_login_whats_this, this::onLinkClick)
            .toCharSequence()

    private fun onLinkClick(id: String) {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.rc_only_login_whats_this_dialog_title)
        dialog.setDescription(getString(R.string.rc_only_login_whats_this_dialog_body))
        showDrawerDialog(dialog)
    }
}
