package io.muun.apollo.presentation.ui.show_qr.bitcoin

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.core.widget.NestedScrollView
import butterknife.BindView
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.model.AddressType
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.selector.BlockchainHeightSelector
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.select_amount.SelectAmountActivity
import io.muun.apollo.presentation.ui.show_qr.QrFragment
import io.muun.apollo.presentation.ui.view.AddressTypeItem
import io.muun.apollo.presentation.ui.view.HiddenSection
import io.muun.common.bitcoinj.BitcoinUri
import javax.money.MonetaryAmount

class BitcoinAddressQrFragment : QrFragment<BitcoinAddressQrPresenter>(),
    BitcoinAddressView,
    AddressTypeItem.AddressTypeChangedListener {

    companion object {
        private const val REQUEST_AMOUNT = 1
    }

    @BindView(R.id.scrollView)
    lateinit var scrollView: NestedScrollView

    @BindView(R.id.address_settings)
    lateinit var hiddenSection: HiddenSection

    @BindView(R.id.address_settings_content)
    lateinit var addressSettingsContent: View

    @BindView(R.id.address_type_item)
    lateinit var addressTypeItem: AddressTypeItem

    // State:

    // Part of our (ugly) hack to allow SATs as an input currency option
    @State
    @JvmField
    var satSelectedAsCurrency = false

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_show_qr

    override fun initializeUi(view: View?) {
        super.initializeUi(view)
        addressTypeItem.setOnAddressTypeChangedListener(this)
        hiddenSection.setOnClickListener { presenter.toggleAdvancedSettings() }
    }

    override fun setShowingAdvancedSettings(showingAdvancedSettings: Boolean) {
        hiddenSection.setExpanded(showingAdvancedSettings)
        if (showingAdvancedSettings) {
            addressSettingsContent.visibility = View.VISIBLE
        }
    }

    override fun setContent(content: String, addressType: AddressType, amount: MonetaryAmount?) {

        // TODO Enable extra QR compression mode. Uppercase bech32 strings are more efficiently
        //  encoded. When? When and if ever popular services like blockchain.info and ledger upgrade
        //  their impls.
        super.setQrContent(content, content)

        // Hackish way to override and show just the address when dealing with a bitcoin uri
        if (amount != null) {
            setShowingText(BitcoinUri(Globals.INSTANCE.network, content).address!!)
        }

        addressTypeItem.show(addressType)

        if (amount != null) {
            editAmountItem.setAmount(amount, getBitcoinUnit())

        } else {
            editAmountItem.resetAmount()
        }
    }

    override fun setTaprootState(blocksToTaproot: Int, status: UserActivatedFeatureStatus) {
        addressTypeItem.taprootStatus = status

        addressTypeItem.hoursToTaproot = BlockchainHeightSelector
            .getBlocksInHours(blocksToTaproot)
    }

    override fun showFullAddress(address: String, addressType: AddressType) {

        val title = when (addressType) {
            AddressType.SEGWIT -> R.string.your_bitcoin_address
            AddressType.LEGACY -> R.string.your_compat_bitcoin_address
            AddressType.TAPROOT -> R.string.your_taproot_bitcoin_address
        }

        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(title)
        dialog.setDescription(address)
        showDrawerDialog(dialog)
    }

    override fun getErrorCorrection(): ErrorCorrectionLevel =
        ErrorCorrectionLevel.H

    override fun toggleAdvancedSettings() {
        hiddenSection.toggleSection()

        if (addressSettingsContent.visibility == View.VISIBLE) {
            addressSettingsContent.visibility = View.GONE

        } else {
            addressSettingsContent.visibility = View.VISIBLE

            scrollView.postDelayed({
                scrollView.fullScroll(View.FOCUS_DOWN)
            }, 100)
        }
    }

    override fun onAddressTypeChanged(newType: AddressType) {
        presenter.switchAddressType(newType)
    }

    override fun onEditAmount(amount: MonetaryAmount?) {
        requestDelegatedExternalResult(
            REQUEST_AMOUNT,
            SelectAmountActivity.getSelectAddressAmountIntent(requireContext(),
                amount,
                satSelectedAsCurrency)
        )
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onExternalResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_AMOUNT && resultCode == Activity.RESULT_OK) {
            this.satSelectedAsCurrency = SelectAmountActivity.getSatSelectedAsCurrencyResult(data!!)
            val result = SelectAmountActivity.getResult(data)

            if (result != null && !result.isZero) {
                presenter.setAmount(result)

            } else {
                presenter.setAmount(null)
            }
        }
    }

    // Part of our (ugly) hack to allow SATs as an input currency option
    private fun getBitcoinUnit() = if (satSelectedAsCurrency) {
        BitcoinUnit.SATS
    } else {
        BitcoinUnit.BTC
    }
}