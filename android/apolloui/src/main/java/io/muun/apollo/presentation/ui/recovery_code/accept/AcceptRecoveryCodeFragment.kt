package io.muun.apollo.presentation.ui.recovery_code.accept

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.AcceptRecoveryCodeFragmentBinding
import io.muun.apollo.databinding.FragmentChangePasswordBinding
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity
import io.muun.apollo.presentation.ui.view.MuunHeader

internal class AcceptRecoveryCodeFragment : SingleFragment<AcceptRecoveryCodePresenter>(),
    AcceptRecoveryCodeView {

    private val binding: AcceptRecoveryCodeFragmentBinding
        get() = getBinding() as AcceptRecoveryCodeFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return AcceptRecoveryCodeFragmentBinding::inflate
    }

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.accept_recovery_code_fragment

    override fun onResume() {
        super.onResume()
        updateAcceptButtonState()
        hideKeyboard(view)
    }

    override fun initializeUi(view: View) {
        with(binding) {
            recoveryCodeAccept.setOnClickListener { presenter?.finishSetup() }

            recoveryCodeCondition1.setOnCheckedChangeListener { _, _ ->
                updateAcceptButtonState()
            }
            recoveryCodeCondition2.setOnCheckedChangeListener { _, _ ->
                updateAcceptButtonState()
            }
        }
    }

    override fun setTexts(user: User) {
        if (!user.hasPassword) {
            binding.recoveryCodeCondition1.setText(R.string.recovery_code_verify_accept_condition_1_skipped_email)
        }
    }

    override fun setUpHeader() {
        val indicatorText = getString(
            R.string.set_up_rc_step_count,
            3,
            SetupRecoveryCodeActivity.SET_UP_RC_STEP_COUNT
        )
        parentActivity.header.apply {
            setIndicatorText(indicatorText)
            setElevated(true)
            setNavigation(MuunHeader.Navigation.EXIT)
        }
    }

    override fun onBackPressed(): Boolean {
        if (!binding.recoveryCodeAccept.isLoading()) {
            presenter.showAbortDialog()
        }
        return true
    }

    override fun setLoading(isLoading: Boolean) {
        with(binding) {
            recoveryCodeAccept.setLoading(isLoading)
            recoveryCodeCondition1.isClickable = !isLoading
            recoveryCodeCondition2.isClickable = !isLoading
        }
    }

    private fun updateAcceptButtonState() {
        with(binding) {
            recoveryCodeAccept.isEnabled = recoveryCodeCondition1.isChecked && recoveryCodeCondition2.isChecked
        }
    }
}