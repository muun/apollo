package io.muun.apollo.presentation.ui.recovery_code.priming

import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader

internal class PrimingRecoveryCodeFragment : SingleFragment<PrimingRecoveryCodePresenter>(),
    PrimingRecoveryCodeView {

    @BindView(R.id.priming_recovery_code_title)
    lateinit var title: TextView

    @BindView(R.id.priming_recovery_code_desc)
    lateinit var description: TextView

    @BindView(R.id.priming_recovery_code_start)
    lateinit var startButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.priming_recovery_code_fragment

    override fun initializeUi(view: View) {
        startButton.setOnClickListener { presenter.continueToShowRecoveryCode() }
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            setElevated(true)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    override fun setTexts(user: User) {
        if (!user.hasPassword) {
            title.setText(R.string.priming_recovery_code_email_skipped_title)
            description.setText(R.string.priming_recovery_code_email_skipped_desc)
        }
    }

    override fun handleLoading(isLoading: Boolean) {
        startButton.setLoading(isLoading)
    }
}