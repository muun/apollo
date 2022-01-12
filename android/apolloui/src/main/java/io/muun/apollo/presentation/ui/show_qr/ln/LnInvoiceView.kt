package io.muun.apollo.presentation.ui.show_qr.ln

import io.muun.apollo.domain.libwallet.DecodedInvoice
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.BaseView
import javax.money.MonetaryAmount

interface LnInvoiceView : BaseView {

    fun setShowingAdvancedSettings(showingAdvancedSettings: Boolean)

    fun setBitcoinUnit(bitcoinUnit: BitcoinUnit)

    fun setLoading(loading: Boolean)

    fun setInvoice(invoice: DecodedInvoice, amount: MonetaryAmount?)

    fun showFullContent(invoice: String)

    fun resetAmount()
}
