package io.muun.apollo.presentation.ui.fragments.rc_only_login_auth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentRcLoginEmailAuthBinding
import io.muun.apollo.presentation.app.Email
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.MuunHeader

class RcLoginEmailAuthorizeFragment : SingleFragment<RcLoginEmailAuthorizePresenter>(),
    RcLoginEmailAuthorizeView {

    private val binding: FragmentRcLoginEmailAuthBinding
        get() = getBinding() as FragmentRcLoginEmailAuthBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentRcLoginEmailAuthBinding::inflate
    }

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.rcLoginEmailAuthTitle.setText(R.string.signup_email_authorize)
        binding.openEmailClient.isEnabled = Email.hasEmailAppInstalled(requireContext())
        binding.openEmailClient.setOnClickListener { presenter.openEmailClient() }
    }

    override fun setUpHeader() {
        val header = parentActivity.header
        header.setNavigation(MuunHeader.Navigation.BACK)
        header.setElevated(true)
        header.showTitle(R.string.login_title)
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    override fun setObfuscatedEmail(obfuscatedEmail: String) {
        val styledDesc = StyledStringRes(
            requireContext(),
            R.string.signup_email_verify_explanation
        )

        binding.rcLoginEmailAuthDescription.text = styledDesc.toCharSequence(obfuscatedEmail)
    }

    override fun setLoading(isLoading: Boolean) {
        binding.rcLoginEmailAuthLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.rcLoginEmailAuthTitle.visibility = if (!isLoading) View.VISIBLE else View.GONE
        binding.rcLoginEmailAuthDescription.visibility = if (!isLoading) View.VISIBLE else View.GONE
        binding.rcLoginEmailAuthIcon.visibility = if (!isLoading) View.VISIBLE else View.GONE
    }

    override fun handleInvalidLinkError() {
        setLoading(false)
        binding.rcLoginEmailAuthIcon.setImageResource(R.drawable.ic_envelope_error)
        binding.rcLoginEmailAuthTitle.setText(R.string.email_link_error_title)
        binding.rcLoginEmailAuthDescription.setText(R.string.authorize_email_link_invalid)
    }

    override fun handleExpiredLinkError() {
        setLoading(false)
        binding.rcLoginEmailAuthIcon.setImageResource(R.drawable.ic_envelope_error)
        binding.rcLoginEmailAuthTitle.setText(R.string.email_link_error_title)
        binding.rcLoginEmailAuthDescription.setText(R.string.authorize_email_link_expired)
    }
}