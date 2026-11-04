package io.muun.apollo.presentation.ui.fragments.setup_password_success

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.databinding.FragmentSetupPasswordSuccessBinding
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragment

class SetupPasswordSuccessFragment : SingleFragment<SetupPasswordSuccessPresenter>(), BaseView {

    private val binding: FragmentSetupPasswordSuccessBinding
        get() = getBinding() as FragmentSetupPasswordSuccessBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentSetupPasswordSuccessBinding::inflate
    }

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View) {
        binding.setupPasswordSuccessAction.setOnClickListener {
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