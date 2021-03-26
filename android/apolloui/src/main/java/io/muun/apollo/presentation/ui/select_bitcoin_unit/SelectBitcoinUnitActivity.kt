package io.muun.apollo.presentation.ui.select_bitcoin_unit

import android.content.Context
import android.content.Intent
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunSettingItem

class SelectBitcoinUnitActivity: BaseActivity<SelectBitcoinUnitPresenter>(), SelectBitcoinUnitView {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, SelectBitcoinUnitActivity::class.java)
    }

    @BindView(R.id.select_currency_header)
    lateinit var header: MuunHeader

    @BindView(R.id.bitcoin_unit_btc)
    lateinit var bitcoinUnitItem: MuunSettingItem

    @BindView(R.id.bitcoin_unit_sat)
    lateinit var satoshisUnitItem: MuunSettingItem

    @State
    @JvmField
    var currencyDisplayMode: CurrencyDisplayMode? = null

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.activity_select_bitcoin_unit

    override fun initializeUi() {
        super.initializeUi()

        header.let {
            it.attachToActivity(this)
            it.showTitle(R.string.select_bitcoin_unit_title)
            it.setNavigation(Navigation.BACK)
        }

        bitcoinUnitItem.setOnClickListener { onItemSelected(CurrencyDisplayMode.BTC) }
        satoshisUnitItem.setOnClickListener { onItemSelected(CurrencyDisplayMode.SATS) }

        updateSelection()
    }

    private fun onItemSelected(newCurrencyDisplayMode: CurrencyDisplayMode) {
        presenter.changeCurrencyDisplayMode(newCurrencyDisplayMode)
    }

    override fun setCurrencyDisplayMode(currencyDisplayMode: CurrencyDisplayMode) {
        this.currencyDisplayMode = currencyDisplayMode
        updateSelection()
    }

    private fun updateSelection() {
        val selectedIcon = UiUtils.getTintedDrawable(
            this,
            R.drawable.ic_check_black_24_px,
            R.color.blue
        )

        satoshisUnitItem.setIcon(null)
        bitcoinUnitItem.setIcon(null)

        if (currencyDisplayMode == CurrencyDisplayMode.BTC) {
            bitcoinUnitItem.setIcon(selectedIcon)

        } else if (currencyDisplayMode == CurrencyDisplayMode.SATS) {
            satoshisUnitItem.setIcon(selectedIcon)
        }
    }
}