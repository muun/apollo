package io.muun.apollo.presentation.ui.recovery_code.accept

import android.view.View
import android.widget.CheckBox
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader

internal class AcceptRecoveryCodeFragment : SingleFragment<AcceptRecoveryCodePresenter>(),
    AcceptRecoveryCodeView {

    @BindView(R.id.recovery_code_condition_1)
    lateinit var condition1: CheckBox

    @BindView(R.id.recovery_code_condition_2)
    lateinit var condition2: CheckBox

    @BindView(R.id.recovery_code_accept)
    lateinit var acceptButton: MuunButton

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
        super.initializeUi(view)

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

        acceptButton.setOnClickListener { presenter!!.finishSetup() }

        condition1.setOnCheckedChangeListener { _, _ ->
            updateAcceptButtonState()
        }
        condition2.setOnCheckedChangeListener { _, _ ->
            updateAcceptButtonState()
        }
    }

    override fun setTexts(user: User) {
        if (!user.hasPassword) {
            condition1.setText(R.string.recovery_code_verify_accept_condition_1_skipped_email)
        }
    }

    override fun onBackPressed(): Boolean {
        if (!acceptButton.isLoading()) {
            presenter.showAbortDialog()
        }
        return true
    }

    override fun setLoading(isLoading: Boolean) {
        acceptButton.setLoading(isLoading)
        condition1.isClickable = !isLoading
        condition2.isClickable = !isLoading
    }

    private fun updateAcceptButtonState() {
        acceptButton.isEnabled = condition1.isChecked && condition2.isChecked
    }
}