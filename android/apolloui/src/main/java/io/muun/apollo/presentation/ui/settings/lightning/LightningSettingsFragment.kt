package io.muun.apollo.presentation.ui.settings.lightning

import android.view.View
import android.view.ViewGroup
import android.widget.Switch
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

class LightningSettingsFragment: SingleFragment<LightningSettingsPresenter>(), LightningSettingsView {

    @BindView(R.id.lightning_settings_loading)
    lateinit var loadingView: LoadingView

    @BindView(R.id.turbo_channels_section)
    lateinit var turboSection: ViewGroup

    @BindView(R.id.turbo_channels_switch)
    lateinit var turboSwitch: SwitchMaterial

    @BindView(R.id.turbo_channels_learn_more)
    lateinit var learnMore: HtmlTextView

    @State
    @JvmField
    var latestState = false

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
            this.toggle(state)
        }

        parentActivity.header.apply {
            showTitle(R.string.settings_lightning)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    private fun toggle(toState: Boolean) {
        if (toState == latestState) {
            // This is our own notification
            return
        }

        if (!toState) {
            val dialog = MuunDialog.Builder()
                    .title(R.string.turbo_channels_disable_title)
                    .message(R.string.turbo_channels_disable_message)
                    .negativeButton(R.string.turbo_channels_disable) {
                        presenter.toggle()
                    }
                    .positiveButton(R.string.cancel) {
                        turboSwitch.isChecked = true
                    }
                    .layout(R.layout.dialog_custom_layout)
                    .build()

            showDialog(dialog)

        } else {
            presenter.toggle()
        }
    }

    private fun openBlog(link: String) {
        requireContext().openInBrowser(link)
    }

    override fun update(enabled: Boolean) {
        latestState = enabled
        turboSwitch.isChecked = enabled
    }

    override fun setLoading(loading: Boolean) {
        turboSection.visibility = if (loading) { View.GONE } else { View.VISIBLE }
        loadingView.visibility = if (loading) { View.VISIBLE } else { View.GONE }
    }
}