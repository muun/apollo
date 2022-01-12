package io.muun.apollo.presentation.ui.fragments.rc_only_login

import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunRecoveryCodeBox

class RcOnlyLoginFragment: SingleFragment<RcOnlyLoginPresenter>(), RcOnlyLoginView {

    @BindView(R.id.rc_only_login_whats_this)
    lateinit var whatsThis: TextView

    @BindView(R.id.rc_only_login_recovery_code_box)
    lateinit var recoveryCodeBox: MuunRecoveryCodeBox

    @BindView(R.id.rc_only_login_continue)
    lateinit var submitButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.fragment_rc_only_login

    override fun initializeUi(view: View?) {

        parentActivity.header.apply {
            setNavigation(MuunHeader.Navigation.BACK)
            showTitle(R.string.login_title)
            setElevated(true)
        }

        recoveryCodeBox.setEditable(true)
        recoveryCodeBox.setOnEditedListener(this::onRecoveryCodeEdited)
        recoveryCodeBox.requestFocusOnFirstEditableSegment()
        recoveryCodeBox.setOnKeyboardNextListeners()
        recoveryCodeBox.setOnKeyboardDoneListener {
            if (submitButton.isEnabled) {
                submitButton.callOnClick()
            }
        }

        whatsThis.text = getWhatThisText()
        submitButton.setOnClickListener(this::onContinueClick)
    }

    override fun onResume() {
        super.onResume()
        onRecoveryCodeEdited(recoveryCodeBox.segmentInputsContent) // re-trigger validation
        recoveryCodeBox.requestFocusOnFirstEditableSegment()
    }

    override fun blockScreenshots(): Boolean {
        return true
    }

    override fun setLoading(loading: Boolean) {
        submitButton.setLoading(loading)
        recoveryCodeBox.setEditable(!loading)
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    override fun setRecoveryCodeError(error: UserFacingError?) {
        recoveryCodeBox.setError(error)
    }

    override fun handleLegacyRecoveryCodeError() {
        recoveryCodeBox.showLegacyRecoveryCodeError { onBackPressed() }
    }

    override fun setConfirmEnabled(enabled: Boolean) {
        submitButton.isEnabled = enabled
    }

    private fun onRecoveryCodeEdited(recoveryCode: String) {
        presenter.onRecoveryCodeEdited(recoveryCode)
    }

    private fun onContinueClick(view: View) {
        presenter.submitRecoveryCode(recoveryCodeBox.segmentInputsContent)
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
