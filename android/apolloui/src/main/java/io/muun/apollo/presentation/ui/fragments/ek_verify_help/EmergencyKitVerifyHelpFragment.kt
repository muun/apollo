package io.muun.apollo.presentation.ui.fragments.ek_verify_help

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentEkVerifyHelpBinding
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.MuunHeader

class EmergencyKitVerifyHelpFragment : SingleFragment<EmergencyKitVerifyHelpPresenter>() {

    private val binding: FragmentEkVerifyHelpBinding
        get() = getBinding() as FragmentEkVerifyHelpBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentEkVerifyHelpBinding::inflate
    }

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View) {
        StyledStringRes(requireContext(), R.string.ek_verify_help_body)
            .toCharSequence()
            .let(binding.subtitle::setText)
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