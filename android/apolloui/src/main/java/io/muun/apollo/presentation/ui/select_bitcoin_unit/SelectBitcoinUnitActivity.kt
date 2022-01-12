package io.muun.apollo.presentation.ui.select_bitcoin_unit

import android.content.Context
import android.content.Intent
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.BitcoinUnit
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
    var bitcoinUnit: BitcoinUnit? = null

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

        bitcoinUnitItem.setOnClickListener { onItemSelected(BitcoinUnit.BTC) }
        satoshisUnitItem.setOnClickListener { onItemSelected(BitcoinUnit.SATS) }

        updateSelection()
    }

    private fun onItemSelected(newBitcoinUnit: BitcoinUnit) {
        presenter.changeBitcoinUnit(newBitcoinUnit)
    }

    override fun setBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit
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

        if (bitcoinUnit == BitcoinUnit.BTC) {
            bitcoinUnitItem.setIcon(selectedIcon)

        } else if (bitcoinUnit == BitcoinUnit.SATS) {
            satoshisUnitItem.setIcon(selectedIcon)
        }
    }
}