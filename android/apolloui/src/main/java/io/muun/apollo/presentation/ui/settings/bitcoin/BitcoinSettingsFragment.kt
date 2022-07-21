package io.muun.apollo.presentation.ui.settings.bitcoin

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import butterknife.BindView
import com.google.android.material.switchmaterial.SwitchMaterial
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.utils.setUserInteractionEnabled
import io.muun.apollo.presentation.ui.view.LoadingView
import io.muun.apollo.presentation.ui.view.MuunHeader

class BitcoinSettingsFragment : SingleFragment<BitcoinSettingsPresenter>(), BitcoinSettingsView {

    @BindView(R.id.taproot_switch)
    lateinit var taprootByDefaultSwitch: SwitchMaterial

    @BindView(R.id.taproot_timer)
    lateinit var taprootTimerContainer: ViewGroup

    @BindView(R.id.taproot_timer_text)
    lateinit var taprootTimerText: TextView

    @BindView(R.id.bitcoin_settings_loading)
    lateinit var loadingView: LoadingView

    @BindView(R.id.taproot_section)
    lateinit var taprootSection: ViewGroup

    @State
    @JvmField
    var taprootByDefault = false

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.bitcoin_settings_fragment
    }

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        taprootByDefaultSwitch.setOnCheckedChangeListener { _, isChecked ->
            this.onTaprootByDefaultChange(isChecked)
        }

        parentActivity.header.apply {
            showTitle(R.string.settings_bitcoin)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    override fun setTaprootByDefault(taprootByDefault: Boolean) {
        this.taprootByDefault = taprootByDefault
        taprootByDefaultSwitch.isChecked = taprootByDefault
    }

    override fun setTaprootStatus(status: UserActivatedFeatureStatus, estimatedHours: Int) {
        taprootByDefaultSwitch.isEnabled = (status == UserActivatedFeatureStatus.ACTIVE)

        if (estimatedHours > 0) {
            taprootTimerContainer.visibility = View.VISIBLE
            taprootTimerText.text =
                getStyledString(R.string.tr_setting_timer, estimatedHours.toString())

        } else {
            taprootTimerContainer.visibility = View.GONE
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
                    presenter.reportTaprootByDefaultChange(newTaprootByDefault)
                }
                .negativeButton(R.string.cancel) {
                    taprootByDefaultSwitch.isChecked = false
                }
                .layout(R.layout.dialog_custom_layout)
                .build()

            showDialog(dialog)

        } else {
            presenter.reportTaprootByDefaultChange(newTaprootByDefault)
        }
    }

    override fun setLoading(loading: Boolean) {
        taprootSection.visibility = if (loading) {
            View.GONE
        } else {
            View.VISIBLE
        }
        loadingView.visibility = if (loading) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}