package io.muun.apollo.presentation.ui.fragments.enter_email

import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.errors.EmailAlreadyUsedError
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.create_email_help.CreateEmailHelpFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunTextInput

class CreateEmailFragment: SingleFragment<CreateEmailPresenter>(), CreateEmailView {

    @BindView(R.id.create_email_subtitle)
    lateinit var subtitle: TextView

    @BindView(R.id.create_email_input)
    lateinit var emailInput: MuunTextInput

    @BindView(R.id.create_email_action)
    lateinit var confirmButton: MuunButton

    @BindView(R.id.create_email_used_help)
    lateinit var alreadyUsedLink: HtmlTextView

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.fragment_create_email
    }

    override fun initializeUi(view: View) {
        super.initializeUi(view)

        // presenter tells parentPresenter to refresh Toolbar title

        StyledStringRes(requireContext(), R.string.create_email_subtitle, this::onSubtitleLinkClick)
            .toCharSequence()
            .let(subtitle::setText)

        emailInput.setOnChangeListener { validateEmailInput() }
        validateEmailInput() // in case it was already set by previous state

        confirmButton.setOnClickListener { presenter.submitEmail(emailInput.text.toString()) }

        StyledStringRes(requireContext(), R.string.create_email_help_link, this::onHelpLinkClick)
            .toCharSequence()
            .let(alreadyUsedLink::setText)
    }

    override fun setLoading(isLoading: Boolean) {
        confirmButton.isLoading = isLoading
        emailInput.isEnabled = !isLoading
    }

    override fun setEmail(email: String?) {
        emailInput.setText(email ?: "")
    }

    override fun setEmailError(error: UserFacingError?) {
        emailInput.setError(error)
        alreadyUsedLink.visibility = if (error is EmailAlreadyUsedError) View.VISIBLE else View.GONE
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    private fun onSubtitleLinkClick(linkId: String) {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.create_email_how_dialog_title)
        dialog.setDescription(getStyledString(R.string.create_email_how_dialog_desc))
        showDrawerDialog(dialog)
    }

    private fun onHelpLinkClick(linkId: String) {
        replaceFragment(CreateEmailHelpFragment(), true)
    }

    private fun validateEmailInput() {
        setEmailError(null)
        confirmButton.isEnabled = presenter.isValidEmail(emailInput.text.toString())
    }
}