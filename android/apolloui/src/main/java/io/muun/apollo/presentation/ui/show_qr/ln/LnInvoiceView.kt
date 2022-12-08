package io.muun.apollo.presentation.ui.show_qr.ln

import io.muun.apollo.domain.libwallet.DecodedInvoice
import io.muun.apollo.presentation.ui.show_qr.QrView
import javax.money.MonetaryAmount

interface LnInvoiceView : QrView {

    fun setShowingAdvancedSettings(showingAdvancedSettings: Boolean)

    fun setLoading(loading: Boolean)

    fun setInvoice(invoice: DecodedInvoice, amount: MonetaryAmount?)

    fun showFullContent(invoice: String)

    fun resetAmount()
}
