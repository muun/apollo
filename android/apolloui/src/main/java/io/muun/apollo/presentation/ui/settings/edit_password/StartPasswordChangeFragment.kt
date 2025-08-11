package io.muun.apollo.presentation.ui.settings.edit_password

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.StartPasswordChangeFragmentBinding
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.getStyledString

class StartPasswordChangeFragment : SingleFragment<StartPasswordChangePresenter>() {

    private val binding: StartPasswordChangeFragmentBinding
        get() = getBinding() as StartPasswordChangeFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return StartPasswordChangeFragmentBinding::inflate
    }

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.start_password_change_fragment
    }

    override fun initializeUi(view: View) {
        with(binding) {
            changePasswordStartExplanationTitle.text =
                getString(R.string.edit_password_explanation_title)
            changePasswordStartExplanation.text =
                getStyledString(R.string.edit_password_explanation)
            changePasswordStart.setOnClickListener {
                changePasswordStart.isEnabled =
                    false // avoid double tap while preparing next Fragment
                presenter.start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.changePasswordStart.isEnabled = true
    }
}
