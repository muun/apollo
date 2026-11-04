package io.muun.apollo.presentation.ui.signup.unverified_rc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.databinding.FragmentRcUnverifiedWarningBinding
import io.muun.apollo.presentation.ui.base.SingleFragment

class UnverifiedRcWarningFragment : SingleFragment<UnverifiedRcWarningPresenter>() {

    private val binding: FragmentRcUnverifiedWarningBinding
        get() = getBinding() as FragmentRcUnverifiedWarningBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        FragmentRcUnverifiedWarningBinding::inflate

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.rcUnverifiedWarningButton.setOnClickListener {
            presenter.proceedToHome()
        }
    }

    override fun setUpHeader() {
        parentActivity.header.visibility = View.GONE
    }
}
