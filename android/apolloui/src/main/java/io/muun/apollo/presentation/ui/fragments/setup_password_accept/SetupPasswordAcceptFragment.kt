package io.muun.apollo.presentation.ui.fragments.setup_password_accept

import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader

class SetupPasswordAcceptFragment :
    SingleFragment<SetupPasswordAcceptPresenter>(), SetupPasswordAcceptView {

    @BindView(R.id.setup_password_accept_subtitle)
    lateinit var subtitle: TextView

    @BindView(R.id.setup_password_accept_condition_1)
    lateinit var condition1: CheckBox

    @BindView(R.id.setup_password_accept_condition_2)
    lateinit var condition2: CheckBox

    @BindView(R.id.setup_password_accept_action)
    lateinit var acceptButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.fragment_setup_password_accept

    override fun initializeUi(view: View) {
        super.initializeUi(view)

        parentActivity.header.setNavigation(MuunHeader.Navigation.EXIT)

        subtitle.text = StyledStringRes(
                requireContext(),
                R.string.setup_password_accept_why, this::onWhyThisClick
        ).toCharSequence()

        condition1.setOnCheckedChangeListener { _, _ -> updateAcceptButtonState() }
        condition2.setOnCheckedChangeListener { _, _ -> updateAcceptButtonState() }

        acceptButton.setOnClickListener {
            presenter.acceptTerms()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAcceptButtonState()
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    override fun setLoading(isLoading: Boolean) {
        acceptButton.isLoading = isLoading
    }

    private fun updateAcceptButtonState() {
        acceptButton.isEnabled = condition1.isChecked && condition2.isChecked
    }

    private fun onWhyThisClick(linkId: String) {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.setup_password_why_cant_reset_title)
        dialog.setDescription(getStyledString(R.string.setup_password_why_cang_reset_desc))
        showDrawerDialog(dialog)

        presenter.reportShowPasswordInfo()
    }
}