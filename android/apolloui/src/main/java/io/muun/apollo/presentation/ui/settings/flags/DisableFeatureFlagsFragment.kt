package io.muun.apollo.presentation.ui.settings.flags

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentDisableFeatureFlagsBinding
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunHeader

class DisableFeatureFlagsFragment : SingleFragment<DisableFeatureFlagsPresenter>(),
    DisableFeatureFlagsView {

    private val binding: FragmentDisableFeatureFlagsBinding
        get() = getBinding() as FragmentDisableFeatureFlagsBinding

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.fragment_disable_feature_flags

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentDisableFeatureFlagsBinding::inflate
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            showTitle(R.string.settings_disable_feature_flags)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        // UI switch represents whether user has FF enabled or not.
        // If switch is ON/checked   -> FF is enabled
        // If switch is OFF/!checked -> FF is disabled
        // Hence, unchecking the switch means the user disables the FF.
        binding.disableFeatureFlagsSecurityCardSwitch.setOnCheckedChangeListener { _, isChecked ->
            presenter.disableSecurityCardFeatureFlag(!isChecked)
        }
    }

    override fun setSecurityCardFlagEnabled(isEnabled: Boolean) {
        binding.disableFeatureFlagsSecurityCardSwitch.isChecked = isEnabled
    }
}