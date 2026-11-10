package io.muun.apollo.presentation.ui.fragments.ek_verify_cloud

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.databinding.FragmentEkCloudVerifyBinding
import io.muun.apollo.presentation.ui.base.SingleFragment

class EmergencyKitCloudVerifyFragment : SingleFragment<EmergencyKitCloudVerifyPresenter>(),
    EmergencyKitCloudVerifyView {

    private val binding: FragmentEkCloudVerifyBinding
        get() = getBinding() as FragmentEkCloudVerifyBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentEkCloudVerifyBinding::inflate
    }

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View) {
        binding.openCloudFile.setOnClickListener {
            presenter.openCloudFile()
        }

        binding.confirm.setOnClickListener {
            presenter.confirmVerify()
        }
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }
}