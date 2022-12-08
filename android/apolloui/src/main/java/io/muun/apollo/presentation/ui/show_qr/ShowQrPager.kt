package io.muun.apollo.presentation.ui.show_qr

import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.apollo.presentation.ui.show_qr.bitcoin.BitcoinAddressQrFragment
import io.muun.apollo.presentation.ui.show_qr.ln.LnInvoiceQrFragment
import io.muun.common.model.ReceiveFormatPreference

class ShowQrPager(userPreferencesSel: UserPreferencesSelector) {

    private val inOrder = when (userPreferencesSel.get().receivePreference) {
        ReceiveFormatPreference.ONCHAIN -> arrayOf(ShowQrPage.BITCOIN, ShowQrPage.LN)
        ReceiveFormatPreference.LIGHTNING -> arrayOf(ShowQrPage.LN, ShowQrPage.BITCOIN)
        ReceiveFormatPreference.UNIFIED -> throw IllegalStateException() // should not happen
    }

    fun at(position: Int): ShowQrPage =
        inOrder[position]

    fun classAt(position: Int) =
        when (at(position)) {
            ShowQrPage.BITCOIN -> BitcoinAddressQrFragment::class
            ShowQrPage.LN -> LnInvoiceQrFragment::class
        }
}