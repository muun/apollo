package io.muun.apollo.presentation.ui.signup.unverified_rc

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton

class UnverifiedRcWarningFragment : SingleFragment<UnverifiedRcWarningPresenter>() {

    @BindView(R.id.rc_unverified_warning_button)
    lateinit var continueButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.fragment_rc_unverified_warning


    override fun initializeUi(view: View) {
        super.initializeUi(view)

        parentActivity.header.visibility = View.GONE

        continueButton.setOnClickListener {
            presenter.proceedToHome()
        }
    }
}