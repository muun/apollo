package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.show_qr.ShowQrPage
import io.muun.apollo.utils.Clipboard
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import io.muun.common.utils.BitcoinUtils
import javax.money.MonetaryAmount

class ReceiveScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    companion object {
        var lastCopiedFromClipboard: String = ""
            private set
    }

    val address: String
        get() {
            id(R.id.show_qr_copy).click()
            lastCopiedFromClipboard = Clipboard.read()
            return lastCopiedFromClipboard
        }

    val invoice: String
        get() {
            normalizedLabel(ShowQrPage.LN.titleRes).click()
            id(R.id.show_qr_copy).click()
            lastCopiedFromClipboard = Clipboard.read()
            return lastCopiedFromClipboard
        }

    fun goToScanLnUrl() {
        desc(R.string.scan_lnurl).click()
    }

    fun addInvoiceAmount(amountInSat: Long) {
        normalizedLabel(ShowQrPage.LN.titleRes).click()
        id(R.id.invoice_settings).click()

        id(R.id.add_amount).click()

        editAmount(BitcoinUtils.satoshisToBitcoins(amountInSat))

        pressMuunButton(R.id.confirm_amount_button)
    }

    private fun editAmount(amount: MonetaryAmount) {
        id(R.id.currency_code).click()
        labelWith(amount.currency.currencyCode).click()

        id(R.id.muun_amount).text = amount.number.toString()
    }
}