package io.muun.apollo.presentation.ui.settings.lightning

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.databinding.LightningSettingsFragmentBinding
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.openInBrowser
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.ReceivePreferenceItem.ReceivePreferenceChangedListener
import io.muun.common.model.ReceiveFormatPreference

class LightningSettingsFragment : SingleFragment<LightningSettingsPresenter>(),
    LightningSettingsView {

    private val binding: LightningSettingsFragmentBinding
        get() = getBinding() as LightningSettingsFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        LightningSettingsFragmentBinding::inflate

    @State
    @JvmField
    var turboChannelsLatestState = false

    @State
    @JvmField
    var currentReceivePreference = ReceiveFormatPreference.ONCHAIN

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        StyledStringRes(requireContext(), R.string.turbo_channels_learn_more, this::openInBrowser)
            .toCharSequence()
            .let(binding.turboChannelsLearnMore::setText)

        binding.turboChannelsSwitch.setOnCheckedChangeListener { _, state ->
            this.toggleTurboChannels(state)
        }

        binding.receivePreferenceItem.setOnReceivePreferenceChangedListener(
            ReceivePreferenceChangedListener { newReceivePreference ->
                if (newReceivePreference == currentReceivePreference) {
                    return@ReceivePreferenceChangedListener
                }

                presenter.updateReceivePreference(newReceivePreference)
            }
        )

        binding.receivePreferenceDescription.text = StyledStringRes(
            requireContext(),
            R.string.settings_receive_preference_description,
            this::openInBrowser
        ).toCharSequence()
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            showTitle(R.string.settings_lightning)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    private fun toggleTurboChannels(toState: Boolean) {
        if (toState == turboChannelsLatestState) {
            // This is our own notification
            return
        }

        if (!toState) {
            val dialog = MuunDialog.Builder()
                .title(R.string.turbo_channels_disable_title)
                .message(R.string.turbo_channels_disable_message)
                .negativeButton(R.string.turbo_channels_disable) {
                    presenter.toggleTurboChannels()
                }
                .positiveButton(R.string.cancel) {
                    binding.turboChannelsSwitch.isChecked = true
                }
                .layout(R.layout.dialog_custom_layout)
                .build()

            showDialog(dialog)
        } else {
            presenter.toggleTurboChannels()
        }
    }

    private fun openInBrowser(link: String) {
        requireContext().openInBrowser(link)
    }

    override fun update(turboChannels: Boolean, receivePreference: ReceiveFormatPreference) {
        turboChannelsLatestState = turboChannels
        currentReceivePreference = receivePreference
        binding.turboChannelsSwitch.isChecked = turboChannels
        binding.receivePreferenceItem.show(receivePreference)
    }

    override fun setLoading(loading: Boolean) {
        binding.turboChannelsSection.visibility = (if (loading) View.GONE else View.VISIBLE)
        binding.lightningSettingsLoading.visibility = (if (loading) View.VISIBLE else View.GONE)
        binding.receivePreferenceSection.visibility = (if (loading) View.GONE else View.VISIBLE)
    }
}
