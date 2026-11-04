package io.muun.apollo.presentation.ui.fragments.verify_email

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentVerifyEmailBinding
import io.muun.apollo.presentation.app.Email
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.MuunHeader

class VerifyEmailFragment : SingleFragment<VerifyEmailPresenter>(), VerifyEmailView {

    override fun inject() {
        component.inject(this)
    }

    private val binding: FragmentVerifyEmailBinding
        get() = getBinding() as FragmentVerifyEmailBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentVerifyEmailBinding::inflate
    }

    override fun initializeUi(view: View) {
        binding.openEmailClient.isEnabled = Email.hasEmailAppInstalled(requireContext())
        binding.openEmailClient.setOnClickListener { presenter.openEmailClient() }
    }

    override fun setEmail(email: String) {
        binding.verifyEmailDescription.text = StyledStringRes(requireContext(), R.string.verify_email_subtitle)
            .toCharSequence(email)
    }

    override fun onBackPressed(): Boolean {
        presenter!!.goBack()
        return true
    }

    override fun setUpHeader() {
        parentActivity.header.setNavigation(MuunHeader.Navigation.BACK)
    }

    override fun setLoading(loading: Boolean) {
        binding.verifyEmailLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.verifyEmailTitle.visibility = if (!loading) View.VISIBLE else View.GONE
        binding.verifyEmailDescription.visibility = if (!loading) View.VISIBLE else View.GONE
        binding.verifyEmailIcon.visibility = if (!loading) View.VISIBLE else View.GONE
    }

    override fun handleInvalidLinkError() {
        setLoading(false)
        binding.verifyEmailIcon.setImageResource(R.drawable.ic_envelope_error)
        binding.verifyEmailTitle.setText(R.string.email_link_error_title)
        binding.verifyEmailDescription.setText(R.string.verify_email_link_invalid)
    }

    override fun handleExpiredLinkError() {
        setLoading(false)
        binding.verifyEmailIcon.setImageResource(R.drawable.ic_envelope_error)
        binding.verifyEmailTitle.setText(R.string.email_link_error_title)
        binding.verifyEmailDescription.setText(R.string.verify_email_link_expired)
    }
}