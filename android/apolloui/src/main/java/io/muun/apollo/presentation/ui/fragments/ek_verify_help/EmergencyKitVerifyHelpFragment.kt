package io.muun.apollo.presentation.ui.fragments.ek_verify_help

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.MuunHeader

class EmergencyKitVerifyHelpFragment : SingleFragment<EmergencyKitVerifyHelpPresenter>() {

    @BindView(R.id.subtitle)
    lateinit var subtitleView: HtmlTextView

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_ek_verify_help

    override fun initializeUi(view: View) {
        StyledStringRes(requireContext(), R.string.ek_verify_help_body)
            .toCharSequence()
            .let(subtitleView::setText)
    }

    override fun setUpHeader() {
        parentActivity.header.let {
            it.setNavigation(MuunHeader.Navigation.EXIT)
            it.hideTitle()
            it.setIndicatorText(null)
            it.setElevated(false)
        }
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }
}