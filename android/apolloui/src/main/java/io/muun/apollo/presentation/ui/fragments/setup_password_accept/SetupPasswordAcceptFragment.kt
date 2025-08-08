package io.muun.apollo.presentation.ui.fragments.setup_password_accept

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentSetupPasswordAcceptBinding
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.MuunHeader

class SetupPasswordAcceptFragment : SingleFragment<SetupPasswordAcceptPresenter>(),
    SetupPasswordAcceptView {

    private val binding: FragmentSetupPasswordAcceptBinding
        get() = getBinding() as FragmentSetupPasswordAcceptBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentSetupPasswordAcceptBinding::inflate
    }

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.fragment_setup_password_accept

    override fun initializeUi(view: View) {
        with(binding) {
            setupPasswordAcceptSubtitle.text = StyledStringRes(
                requireContext(),
                R.string.setup_password_accept_why, this@SetupPasswordAcceptFragment::onWhyThisClick
            ).toCharSequence()

            setupPasswordAcceptCondition1.setOnCheckedChangeListener { _, _ -> updateAcceptButtonState() }
            setupPasswordAcceptCondition2.setOnCheckedChangeListener { _, _ -> updateAcceptButtonState() }

            setupPasswordAcceptAction.setOnClickListener {
                presenter.acceptTerms()
            }
        }
    }

    override fun setUpHeader() {
        parentActivity.header.setNavigation(MuunHeader.Navigation.EXIT)
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
        binding.setupPasswordAcceptAction.setLoading(isLoading)
    }

    private fun updateAcceptButtonState() {
        with(binding) {
            setupPasswordAcceptAction.isEnabled =
                setupPasswordAcceptCondition1.isChecked && setupPasswordAcceptCondition2.isChecked
        }
    }

    private fun onWhyThisClick(linkId: String) {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.setup_password_why_cant_reset_title)
        dialog.setDescription(getStyledString(R.string.setup_password_why_cang_reset_desc))
        showDrawerDialog(dialog)

        presenter.reportShowPasswordInfo()
    }
}