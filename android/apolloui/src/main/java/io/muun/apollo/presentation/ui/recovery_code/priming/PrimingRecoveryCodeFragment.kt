package io.muun.apollo.presentation.ui.recovery_code.priming

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.PrimingRecoveryCodeFragmentBinding
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunHeader

internal class PrimingRecoveryCodeFragment : SingleFragment<PrimingRecoveryCodePresenter>(),
    PrimingRecoveryCodeView {

    private val binding: PrimingRecoveryCodeFragmentBinding
        get() = getBinding() as PrimingRecoveryCodeFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        PrimingRecoveryCodeFragmentBinding::inflate

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.primingRecoveryCodeStart.setOnClickListener { presenter.continueToShowRecoveryCode() }
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            setElevated(true)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    override fun setTexts(user: User) {
        if (!user.hasPassword) {
            binding.primingRecoveryCodeTitle.setText(R.string.priming_recovery_code_email_skipped_title)
            binding.primingRecoveryCodeDesc.setText(R.string.priming_recovery_code_email_skipped_desc)
        }
    }

    override fun handleLoading(isLoading: Boolean) {
        binding.primingRecoveryCodeStart.setLoading(isLoading)
    }
}