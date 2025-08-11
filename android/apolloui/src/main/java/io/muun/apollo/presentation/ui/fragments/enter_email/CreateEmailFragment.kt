package io.muun.apollo.presentation.ui.fragments.enter_email

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentCreateEmailBinding
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.domain.errors.passwd.EmailAlreadyUsedError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.create_email_help.CreateEmailHelpFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getStyledString

class CreateEmailFragment : SingleFragment<CreateEmailPresenter>(), CreateEmailView {

    private val binding: FragmentCreateEmailBinding
        get() = getBinding() as FragmentCreateEmailBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentCreateEmailBinding::inflate
    }

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.fragment_create_email
    }

    override fun initializeUi(view: View) {
        with(binding) {
            val styledCreateEmailSubtitle = StyledStringRes(
                requireContext(),
                R.string.create_email_subtitle,
                this@CreateEmailFragment::onSubtitleLinkClick,
            )
            styledCreateEmailSubtitle.toCharSequence()
                .let(createEmailSubtitle::setText)

            createEmailInput.setOnChangeListener(this@CreateEmailFragment) {
                validateEmailInput()
            }
            validateEmailInput() // in case it was already set by previous state

            createEmailAction.setOnClickListener { presenter.submitEmail(createEmailInput.text.toString()) }

            val styledHelpEmail = StyledStringRes(
                requireContext(),
                R.string.create_email_help_link,
                this@CreateEmailFragment::onHelpLinkClick,
            )
            styledHelpEmail.toCharSequence()
                .let(createEmailUsedHelp::setText)
        }
    }

    override fun setUpHeader() {
        // Presenter tells parentPresenter to refresh Toolbar title. This is an exceptional case, as
        // we're in the middle of a flow, where the header is being set up as it will be displayed
        // for several other fragments (nice that logic lives in activity) but also text displayed
        // depends on user state.
        // Yeah. This is confusing. Sorry for that. I didn't want to lose this comment, since it
        // kinda signals a code smell. This is a use case that our current implementation does not
        // handle very well. In the past we've had some trouble with this, and this comment arose
        // to warn and help maintainers from making mistakes.
        // TODO improve our SetUpHeader impl to handle this use case better
    }

    override fun setLoading(isLoading: Boolean) {
        binding.createEmailAction.setLoading(isLoading)
        binding.createEmailInput.isEnabled = !isLoading
    }

    override fun setEmail(email: String?) {
        binding.createEmailInput.setText(email ?: "")
    }

    override fun setEmailError(error: UserFacingError?) {
        binding.createEmailInput.setError(error)
        binding.createEmailUsedHelp.visibility = if (error is EmailAlreadyUsedError) View.VISIBLE else View.GONE
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
        binding.createEmailAction.isEnabled = presenter.isValidEmail(binding.createEmailInput.text.toString())
    }
}