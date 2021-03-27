package io.muun.apollo.presentation.ui.show_qr.bitcoin

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.core.widget.NestedScrollView
import butterknife.BindView
import butterknife.OnClick
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.select_amount.SelectAmountActivity
import io.muun.apollo.presentation.ui.show_qr.QrFragment
import io.muun.apollo.presentation.ui.view.AddressTypeItem
import io.muun.apollo.presentation.ui.view.EditAmountItem
import io.muun.apollo.presentation.ui.view.HiddenSection
import io.muun.common.bitcoinj.BitcoinUri
import javax.money.MonetaryAmount

class BitcoinAddressQrFragment : QrFragment<BitcoinAddressQrPresenter>(),
    BitcoinAddressView,
    AddressTypeItem.AddresTypeChangedListener,
    EditAmountItem.EditAmountHandler {

    companion object {
        private const val REQUEST_AMOUNT = 1
    }

    @BindView(R.id.scrollView)
    lateinit var scrollView: NestedScrollView

    @BindView(R.id.address_settings)
    lateinit var hiddenSection: HiddenSection

    @BindView(R.id.address_settings_content)
    lateinit var addressSettingsContent: View

    @BindView(R.id.edit_amount_item)
    lateinit var editAmountItem: EditAmountItem

    @BindView(R.id.address_type_item)
    lateinit var addressTypeItem: AddressTypeItem

    // State:

    @State
    lateinit var mode: CurrencyDisplayMode

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_show_qr

    override fun initializeUi(view: View?) {
        super.initializeUi(view)
        editAmountItem.setEditAmountHandler(this)
        addressTypeItem.setOnAddressTypeChangedListener(this)
    }

    override fun setShowingAdvancedSettings(showingAdvancedSettings: Boolean) {
        hiddenSection.setExpanded(showingAdvancedSettings)
        if (showingAdvancedSettings) {
            addressSettingsContent.visibility = View.VISIBLE
        }
    }

    override fun setCurrencyDisplayMode(mode: CurrencyDisplayMode) {
        this.mode = mode
    }

    override fun setContent(content: String, addressType: AddressType, amount: MonetaryAmount?) {
        super.setQrContent(content)

        // Hackish way to override and show just the address when dealing with a bitcoin uri
        if (amount != null) {
            setShowingText(BitcoinUri(Globals.INSTANCE.network, content).address!!)
        }

        addressTypeItem.show(addressType)

        if (amount != null) {
            editAmountItem.setAmount(amount, mode)

        } else {
            editAmountItem.resetAmount()
        }
    }

    override fun showFullAddress(address: String, addressType: AddressType) {

        val title = when (addressType) {
            AddressType.SEGWIT -> R.string.your_bitcoin_address
            AddressType.LEGACY -> R.string.your_compat_bitcoin_address
        }

        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(title)
        dialog.setDescription(address)
        showDrawerDialog(dialog)
    }

    override fun preProcessQrContent(content: String): String =
        content // No pre-processing

    override fun getErrorCorrection(): ErrorCorrectionLevel =
        ErrorCorrectionLevel.H

    @OnClick(R.id.address_settings)
    fun onAddressSettingsClick() {
        presenter.toggleAdvancedSettings()
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
            SelectAmountActivity.getSelectAddressAmountIntent(requireContext(), amount)
        )
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onExternalResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_AMOUNT && resultCode == Activity.RESULT_OK) {
            val result = SelectAmountActivity.getResult(data!!)

            if (result != null && !result.isZero) {
                presenter.setAmount(result)

            } else {
                presenter.setAmount(null)
            }
        }
    }
}