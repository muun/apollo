package io.muun.apollo.presentation.ui.show_qr.ln

import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.common.utils.LnInvoice

interface LnInvoiceView : BaseView {

    fun setLoading(loading: Boolean)

    fun setInvoice(invoice: LnInvoice)

    fun showFullContent(invoice: String)
}
