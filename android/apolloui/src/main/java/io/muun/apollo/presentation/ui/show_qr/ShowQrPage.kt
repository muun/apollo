package io.muun.apollo.presentation.ui.show_qr

import androidx.annotation.StringRes
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.show_qr.bitcoin.BitcoinAddressQrFragment
import io.muun.apollo.presentation.ui.show_qr.ln.LnInvoiceQrFragment

enum class ShowQrPage(@StringRes val titleRes: Int) {
    BITCOIN(R.string.tab_bitcoin_address),
    LN(R.string.tab_ln_invoice);

    fun getIndex(): Int {
        return inOrder.indexOf(this)
    }

    companion object {
        val inOrder = arrayOf(BITCOIN, LN)

        fun at(position: Int) =
            inOrder[position]

        fun classAt(position: Int) =
            when (at(position)) {
                BITCOIN -> BitcoinAddressQrFragment::class
                LN -> LnInvoiceQrFragment::class
            }
    }
}