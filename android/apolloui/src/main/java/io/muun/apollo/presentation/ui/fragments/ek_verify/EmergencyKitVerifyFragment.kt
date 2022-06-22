package io.muun.apollo.presentation.ui.fragments.ek_verify

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunTextInput


class EmergencyKitVerifyFragment: SingleFragment<EmergencyKitVerifyPresenter>(),
                                  EmergencyKitVerifyView {

    @BindView(R.id.code_input)
    lateinit var verificationCodeInput: MuunTextInput

    @BindView(R.id.ek_verify_action)
    lateinit var verifyButton: MuunButton

    @BindView(R.id.need_help)
    lateinit var needHelpView: HtmlTextView;


    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_ek_verify

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        verificationCodeInput.setOnChangeListener(this) {
            verifyButton.isEnabled = verificationCodeInput.text.isNotEmpty()
        }

        verifyButton.setOnClickListener {
            presenter.verifyCode(verificationCodeInput.text.toString())
        }

        StyledStringRes(requireContext(), R.string.ek_verify_need_help, this::onNeedHelpClick)
            .toCharSequence()
            .let(needHelpView::setText)
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    override fun setLoading(isLoading: Boolean) {
        verifyButton.setLoading(isLoading)
    }

    override fun setVerificationError(error: UserFacingError) {
        verificationCodeInput.setError(error)
    }

    private fun onNeedHelpClick(linkId: String) {
        presenter.showHelp()
    }
}