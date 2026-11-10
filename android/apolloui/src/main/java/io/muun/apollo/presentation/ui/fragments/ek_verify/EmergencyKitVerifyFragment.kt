package io.muun.apollo.presentation.ui.fragments.ek_verify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentEkVerifyBinding
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes

class EmergencyKitVerifyFragment : SingleFragment<EmergencyKitVerifyPresenter>(),
    EmergencyKitVerifyView {

    private val binding: FragmentEkVerifyBinding
        get() = getBinding() as FragmentEkVerifyBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentEkVerifyBinding::inflate
    }

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View) {
        binding.codeInput.setOnChangeListener(this) {
            binding.ekVerifyAction.isEnabled = binding.codeInput.text.isNotEmpty()
        }

        binding.ekVerifyAction.setOnClickListener {
            presenter.verifyCode(binding.codeInput.text.toString())
        }

        StyledStringRes(requireContext(), R.string.ek_verify_need_help, this::onNeedHelpClick)
            .toCharSequence()
            .let(binding.needHelp::setText)
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
        binding.ekVerifyAction.setLoading(isLoading)
    }

    override fun setVerificationError(error: UserFacingError) {
        binding.codeInput.setError(error)
    }

    private fun onNeedHelpClick(linkId: String) {
        presenter.showHelp()
    }
}