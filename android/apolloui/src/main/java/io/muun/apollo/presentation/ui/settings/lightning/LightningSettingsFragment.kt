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

    @BindView(R.id.lightning_default_section)
    lateinit var lightningSection: ViewGroup

    @BindView(R.id.lightning_default_switch)
    lateinit var lightningSwitch: SwitchMaterial


    @State
    @JvmField
    var turboChannelsLatestState = false

    @State
    @JvmField
    var lightningDefaultLatestState = false

    // For now, we won't release this. Remove this flag once we do.
    val lightningByDefaultOn = false

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.lightning_settings_fragment
    }

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        StyledStringRes(requireContext(), R.string.turbo_channels_learn_more, this::openBlog)
            .toCharSequence()
            .let(learnMore::setText)

        turboSwitch.setOnCheckedChangeListener { _, state ->
            this.toggleTurboChannels(state)
        }

        lightningSwitch.setOnCheckedChangeListener { _, state ->
            this.toggleLightningDefault(state)
        }

        parentActivity.header.apply {
            showTitle(R.string.settings_lightning)
            setNavigation(MuunHeader.Navigation.BACK)
        }

        lightningSection.visibility = if (lightningByDefaultOn) View.VISIBLE else View.GONE
    }

    private fun toggleLightningDefault(toState: Boolean) {
        if (toState == lightningDefaultLatestState) {
            return
        }

        presenter.toggleLightningForReceiving()
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

    private fun openBlog(link: String) {
        requireContext().openInBrowser(link)
    }

    override fun update(turboChannels: Boolean, lightningDefaultForReceiving: Boolean) {
        turboChannelsLatestState = turboChannels
        lightningDefaultLatestState = lightningDefaultForReceiving
        turboSwitch.isChecked = turboChannels
        lightningSwitch.isChecked = lightningDefaultForReceiving
    }

    override fun setLoading(loading: Boolean) {
        turboSection.visibility = (if (loading) View.GONE else View.VISIBLE)
        loadingView.visibility = (if (loading) View.VISIBLE else View.GONE)
        if (lightningByDefaultOn) {
            lightningSection.visibility = (if (loading) View.GONE else View.VISIBLE)
        }
    }
}