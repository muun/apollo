package io.muun.apollo.presentation.ui.recovery_code.show

import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.libwallet.RecoveryCodeV2
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunRecoveryCodeBox

internal class ShowRecoveryCodeFragment : SingleFragment<ShowRecoveryCodePresenter>(),
    ShowRecoveryCodeView {

    @BindView(R.id.explanation)
    lateinit var explanationView: TextView

    @BindView(R.id.recovery_code_box)
    lateinit var recoveryCodeBox: MuunRecoveryCodeBox

    @BindView(R.id.recovery_code_continue)
    lateinit var continueButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.show_recovery_code_fragment
    }

    override fun initializeUi(view: View) {
        super.initializeUi(view)
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

        continueButton.setOnClickListener {
            presenter.continueToVerification()
        }
    }

    override fun blockScreenshots(): Boolean =
        true

    override fun setRecoveryCode(recoveryCode: RecoveryCodeV2) {
        recoveryCodeBox.setRecoveryCode(recoveryCode)
    }

    override fun onBackPressed(): Boolean {
        presenter.showAbortDialog()
        return true
    }
}