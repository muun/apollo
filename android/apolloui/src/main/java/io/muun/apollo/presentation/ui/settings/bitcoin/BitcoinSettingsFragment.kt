package io.muun.apollo.presentation.ui.settings.bitcoin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.databinding.BitcoinSettingsFragmentBinding
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.MuunHeader

class BitcoinSettingsFragment : SingleFragment<BitcoinSettingsPresenter>(), BitcoinSettingsView {

    private val binding: BitcoinSettingsFragmentBinding
        get() = getBinding() as BitcoinSettingsFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        BitcoinSettingsFragmentBinding::inflate

    @State
    @JvmField
    var taprootByDefault = false

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.taprootSwitch.setOnCheckedChangeListener { _, isChecked ->
            this.onTaprootByDefaultChange(isChecked)
        }
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            showTitle(R.string.settings_bitcoin)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    override fun setTaprootByDefault(taprootByDefault: Boolean) {
        this.taprootByDefault = taprootByDefault
        binding.taprootSwitch.isChecked = taprootByDefault
    }

    override fun setTaprootStatus(status: UserActivatedFeatureStatus, estimatedHours: Int) {
        binding.taprootSwitch.isEnabled = (status == UserActivatedFeatureStatus.ACTIVE)

        if (estimatedHours > 0) {
            binding.taprootTimer.visibility = View.VISIBLE
            binding.taprootTimerText.text =
                getStyledString(R.string.tr_setting_timer, estimatedHours.toString())
        } else {
            binding.taprootTimer.visibility = View.GONE
        }
    }

    private fun onTaprootByDefaultChange(newTaprootByDefault: Boolean) {
        if (newTaprootByDefault == taprootByDefault) {
            return // we triggered this by changing the value internally
        }

        if (newTaprootByDefault) {
            val dialog = MuunDialog.Builder()
                .title(R.string.tr_setting_confirm_title)
                .message(R.string.tr_setting_confirm_desc)
                .positiveButton(R.string.tr_setting_confirm) {
                    presenter.reportTaprootByDefaultChange(true)
                }
                .negativeButton(R.string.cancel) {
                    binding.taprootSwitch.isChecked = false
                }
                .layout(R.layout.dialog_custom_layout)
                .build()

            showDialog(dialog)
        } else {
            presenter.reportTaprootByDefaultChange(false)
        }
    }

    override fun setLoading(loading: Boolean) {
        binding.taprootSection.visibility = if (loading) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.bitcoinSettingsLoading.visibility = if (loading) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}
