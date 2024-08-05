package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.libwallet.BitcoinUri
import io.muun.apollo.domain.model.AddressType
import io.muun.apollo.presentation.ui.show_qr.ShowQrPage
import io.muun.apollo.utils.Clipboard
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import io.muun.common.bitcoinj.ValidationHelpers
import io.muun.common.model.ReceiveFormatPreference
import io.muun.common.utils.Bech32SegwitAddress
import io.muun.common.utils.BitcoinUtils
import org.assertj.core.api.Assertions.assertThat
import javax.money.MonetaryAmount

class ReceiveScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    companion object {
        var lastCopiedFromClipboard: String = ""
            private set
    }

    sealed class UnifiedQrDraft {
        data class OnChain(
            val amount: MonetaryAmount? = null,
            val addressType: AddressType = AddressType.SEGWIT,
        ) : UnifiedQrDraft()

        data class OffChain(
            val amountInSat: Long? = null,
            val turboChannel: Boolean = true,
        ) : UnifiedQrDraft()
    }

    val address: String
        get() {
            id(R.id.show_qr_copy).click()
            lastCopiedFromClipboard = Clipboard.read()
            return lastCopiedFromClipboard
        }

    val bitcoinUri: String
        get() = address // Yes, its obtained the same way, but callers should set up an amount first

    val invoice: String
        get() {
            normalizedLabel(ShowQrPage.LN.titleRes).click()
            id(R.id.show_qr_copy).click()
            lastCopiedFromClipboard = Clipboard.read()
            return lastCopiedFromClipboard
        }

    val unifiedQr: String
        get() {
            id(R.id.show_qr_copy).click()
            lastCopiedFromClipboard = Clipboard.read()
            return lastCopiedFromClipboard
        }

    fun goToScanLnUrl() {
        desc(R.string.scan_lnurl).click()
    }

    fun selectAddressType(addressType: AddressType) {

        if (!id(R.id.edit_address_type).exists()) {
            id(R.id.address_settings).click()
        }

        id(R.id.edit_address_type).click()

        when (addressType) {
            AddressType.SEGWIT -> label(R.string.address_picker_segwit_title).click()
            AddressType.LEGACY -> label(R.string.address_picker_legacy_title).click()
            AddressType.TAPROOT -> label(R.string.address_picker_taproot_title).click()
        }
    }

    private fun selectAddressTypeForUnifiedQr(addressType: AddressType) {

        if (!id(R.id.edit_address_type).exists()) {
            id(R.id.unified_qr_settings).click()
        }

        id(R.id.edit_address_type).click()

        when (addressType) {
            AddressType.SEGWIT -> label(R.string.address_picker_segwit_title).click()
            AddressType.LEGACY -> label(R.string.address_picker_legacy_title).click()
            AddressType.TAPROOT -> label(R.string.address_picker_taproot_title).click()
        }
    }

    fun addUnifiedQrAmount(amountInSat: Long) {
        id(R.id.unified_qr_settings).click()

        editAmount(amountInSat)
    }

    fun addInvoiceAmount(amountInSat: Long) {
        normalizedLabel(ShowQrPage.LN.titleRes).click()
        id(R.id.invoice_settings).click()

        editAmount(amountInSat)
    }

    fun addBitcoinUriAmount(amount: MonetaryAmount) {
        normalizedLabel(ShowQrPage.BITCOIN.titleRes).click()
        id(R.id.address_settings).click()

        editAmount(amount)
    }

    private fun editAmount(amountInSat: Long) {
        editAmount(BitcoinUtils.satoshisToBitcoins(amountInSat))
    }

    private fun editAmount(amount: MonetaryAmount) {
        if (id(R.id.add_amount).exists()) {
            id(R.id.add_amount).click()

        } else {
            id(R.id.amount_label).click()
        }

        id(R.id.currency_code).click()
        labelWith(amount.currency.currencyCode).click()

        id(R.id.muun_amount).text = amount.number.toString()

        pressMuunButton(R.id.confirm_amount_button)
    }

    fun checkReceivePreferenceIs(receiveFormatPreference: ReceiveFormatPreference) {
        when (receiveFormatPreference) {
            ReceiveFormatPreference.ONCHAIN -> {
                receiveScreen.id(R.id.address_settings).exists()
                receiveScreen.id(R.id.invoice_settings).assertDoesntExist()
                receiveScreen.id(R.id.unified_qr_settings).assertDoesntExist()
            }
            ReceiveFormatPreference.LIGHTNING -> {
                receiveScreen.id(R.id.address_settings).assertDoesntExist()
                receiveScreen.id(R.id.invoice_settings).exists()
                receiveScreen.id(R.id.unified_qr_settings).assertDoesntExist()
            }
            ReceiveFormatPreference.UNIFIED -> {
                receiveScreen.id(R.id.address_settings).assertDoesntExist()
                receiveScreen.id(R.id.invoice_settings).assertDoesntExist()
                receiveScreen.id(R.id.unified_qr_settings).exists()
            }
        }
    }

    fun checkUnifiedQrConfig() {
        var amountInSat: Long = 100
        var addressType = AddressType.SEGWIT

        addUnifiedQrAmount(amountInSat)
        selectAddressTypeForUnifiedQr(addressType)
        checkUnifiedQrConfig(amountInSat, addressType)

        amountInSat = 200
        addressType = AddressType.LEGACY

        changeAndCheck(amountInSat, addressType)

        amountInSat = 300
        addressType = AddressType.TAPROOT

        changeAndCheck(amountInSat, addressType)

        device.pressBack()
    }

    private fun changeAndCheck(amountInSat: Long, addressType: AddressType) {
        editAmount(amountInSat)
        selectAddressTypeForUnifiedQr(addressType)

    }

    private fun checkUnifiedQrConfig(
        amountInSat: Long,
        addressType: AddressType,
    ) {
        checkAmountIs(amountInSat)
        checkAddressTypeIs(addressType)
        id(R.id.expiration_time_item).exists()
    }

    private fun checkAmountIs(amountInSat: Long) {
        checkAmountIs(BitcoinUtils.satoshisToBitcoins(amountInSat))
    }

    private fun checkAmountIs(expectedAmount: MonetaryAmount) {
        val selectedAmount = id(R.id.selected_amount).text.toMoney()
        assertMoneyEqualsWithRoundingHack(selectedAmount, expectedAmount)
    }

    private fun checkAddressTypeIs(expectedAddressType: AddressType) {
        val addressType = id(R.id.edit_address_type).text

        val (address, _, _) = BitcoinUri.parse(unifiedQr)
        val params = Globals.INSTANCE.network

        when (expectedAddressType) {
            AddressType.SEGWIT -> {
                assert(Bech32SegwitAddress.decode(params, address).fst.toInt() == 0)
                assertThat(addressType).isEqualTo(context.getString(R.string.segwit))
            }

            AddressType.LEGACY -> {
                assert(ValidationHelpers.isValidBase58Address(params, address))
                assertThat(addressType).isEqualTo(context.getString(R.string.legacy))
            }

            AddressType.TAPROOT -> {
                assert(Bech32SegwitAddress.decode(params, address).fst.toInt() == 1)
                assertThat(addressType).isEqualTo(context.getString(R.string.taproot))
            }
        }
    }
}