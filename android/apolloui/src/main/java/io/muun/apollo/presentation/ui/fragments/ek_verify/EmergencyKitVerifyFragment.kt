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


class EmergencyKitVerifyFragment : SingleFragment<EmergencyKitVerifyPresenter>(),
    EmergencyKitVerifyView {

    @BindView(R.id.code_input)
    lateinit var verificationCodeInput: MuunTextInput

    @BindView(R.id.ek_verify_action)
    lateinit var verifyButton: MuunButton

    @BindView(R.id.need_help)
    lateinit var needHelpView: HtmlTextView

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_ek_verify

    override fun initializeUi(view: View) {
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

    override fun setUpHeader() {
        // Presenter tells parentPresenter to refresh Toolbar. This is an exceptional case, as we're
        // in the middle of a flow, and we want to refresh the same header setup as the activity
        // (some other fragments of the flow may have change the header), and we also need to
        // handle back navigation.
        // Yeah. This is confusing. Sorry for that. I didn't want to lose this comment, since it
        // kinda signals a code smell. This is a use case that our current implementation does not
        // handle very well. In the past we've had some trouble with this, and this comment arose
        // to warn and help maintainers from making mistakes.
        // TODO improve our SetUpHeader impl to handle this use case better
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