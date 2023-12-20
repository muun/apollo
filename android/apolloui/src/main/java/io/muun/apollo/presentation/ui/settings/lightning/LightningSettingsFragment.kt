package io.muun.apollo.presentation.ui.settings.lightning

import android.view.View
import android.view.ViewGroup
import butterknife.BindView
import com.google.android.material.switchmaterial.SwitchMaterial
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.openInBrowser
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.LoadingView
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.ReceivePreferenceItem
import io.muun.apollo.presentation.ui.view.ReceivePreferenceItem.ReceivePreferenceChangedListener
import io.muun.common.model.ReceiveFormatPreference

class LightningSettingsFragment : SingleFragment<LightningSettingsPresenter>(),
    LightningSettingsView {

    @BindView(R.id.lightning_settings_loading)
    lateinit var loadingView: LoadingView

    @BindView(R.id.turbo_channels_section)
    lateinit var turboSection: ViewGroup

    @BindView(R.id.turbo_channels_switch)
    lateinit var turboSwitch: SwitchMaterial

    @BindView(R.id.turbo_channels_learn_more)
    lateinit var learnMore: HtmlTextView

    @BindView(R.id.receive_preference_section)
    lateinit var receivePreference: ViewGroup

    @BindView(R.id.receive_preference_item)
    lateinit var receivePreferenceItem: ReceivePreferenceItem

    @BindView(R.id.receive_preference_description)
    lateinit var receivePreferenceDescription: HtmlTextView

    @State
    @JvmField
    var turboChannelsLatestState = false

    @State
    @JvmField
    var currentReceivePreference = ReceiveFormatPreference.ONCHAIN

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.lightning_settings_fragment
    }

    override fun initializeUi(view: View) {
        StyledStringRes(requireContext(), R.string.turbo_channels_learn_more, this::openInBrowser)
            .toCharSequence()
            .let(learnMore::setText)

        turboSwitch.setOnCheckedChangeListener { _, state ->
            this.toggleTurboChannels(state)
        }

        receivePreferenceItem.setOnReceivePreferenceChangedListener(
            ReceivePreferenceChangedListener { newReceivePreference ->
                if (newReceivePreference == currentReceivePreference) {
                    return@ReceivePreferenceChangedListener
                }

                presenter.updateReceivePreference(newReceivePreference)
            }
        )

        receivePreferenceDescription.text = StyledStringRes(
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
                    turboSwitch.isChecked = true
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
        turboSwitch.isChecked = turboChannels
        receivePreferenceItem.show(receivePreference)
    }

    override fun setLoading(loading: Boolean) {
        turboSection.visibility = (if (loading) View.GONE else View.VISIBLE)
        loadingView.visibility = (if (loading) View.VISIBLE else View.GONE)
        receivePreference.visibility = (if (loading) View.GONE else View.VISIBLE)
    }
}