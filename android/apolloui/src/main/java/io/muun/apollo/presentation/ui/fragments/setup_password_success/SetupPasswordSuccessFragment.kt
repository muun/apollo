package io.muun.apollo.presentation.ui.fragments.setup_password_success

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton

class SetupPasswordSuccessFragment : SingleFragment<SetupPasswordSuccessPresenter>(), BaseView {

    @BindView(R.id.setup_password_success_action)
    lateinit var actionButton: MuunButton

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_setup_password_success

    override fun initializeUi(view: View) {
        actionButton.setOnClickListener {
            presenter.finishSetup()
        }
    }

    override fun setUpHeader() {
        parentActivity.header.visibility = View.GONE
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }
}