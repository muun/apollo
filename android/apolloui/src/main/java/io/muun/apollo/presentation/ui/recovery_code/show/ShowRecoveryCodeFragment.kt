package io.muun.apollo.presentation.ui.recovery_code.show

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ShowRecoveryCodeFragmentBinding
import io.muun.apollo.domain.libwallet.RecoveryCodeV2
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity
import io.muun.apollo.presentation.ui.view.MuunHeader

internal class ShowRecoveryCodeFragment : SingleFragment<ShowRecoveryCodePresenter>(),
    ShowRecoveryCodeView {

    private val binding: ShowRecoveryCodeFragmentBinding
        get() = getBinding() as ShowRecoveryCodeFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        ShowRecoveryCodeFragmentBinding::inflate

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.recoveryCodeContinue.setOnClickListener {
            presenter.continueToVerification()
        }
    }

    override fun setUpHeader() {
        val indicatorText = getString(
            R.string.set_up_rc_step_count,
            1,
            SetupRecoveryCodeActivity.SET_UP_RC_STEP_COUNT
        )
        parentActivity.header.apply {
            setIndicatorText(indicatorText)
            setElevated(true)
            setNavigation(MuunHeader.Navigation.EXIT)
        }
    }

    override fun blockScreenshots(): Boolean =
        true

    override fun setRecoveryCode(recoveryCode: RecoveryCodeV2) {
        binding.recoveryCodeBox.setRecoveryCode(recoveryCode)
    }

    override fun onBackPressed(): Boolean {
        presenter.showAbortDialog()
        return true
    }
}