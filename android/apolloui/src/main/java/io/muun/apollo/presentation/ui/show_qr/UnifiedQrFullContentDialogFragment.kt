package io.muun.apollo.presentation.ui.show_qr

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.openInBrowser
import io.muun.apollo.presentation.ui.view.DrawerDialogFragment
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.MuunActionDrawer

class UnifiedQrFullContentDialogFragment : DrawerDialogFragment() {

    private lateinit var address: String
    private lateinit var invoice: String

    private lateinit var addressCopyListener: View.OnClickListener
    private lateinit var invoiceCopyListener: View.OnClickListener

    fun setAddress(address: String) {
        this.address = address
    }

    fun setInvoice(invoice: String) {
        this.invoice = invoice
    }

    fun setOnAddressClickListener(listener: View.OnClickListener) {
        this.addressCopyListener = listener
    }

    fun setOnInvoiceClickListener(listener: View.OnClickListener) {
        this.invoiceCopyListener = listener
    }

    override fun createActionDrawer(): MuunActionDrawer {
        val context = requireContext()
        val drawer = UnifiedQrFullContentDrawer(context)

        drawer.setAddress(address)
        drawer.setInvoice(invoice)

        drawer.setOnAddressCopyListener(addressCopyListener)
        drawer.setOnInvoiceCopyListener(invoiceCopyListener)

        return drawer
    }

    class UnifiedQrFullContentDrawer : MuunActionDrawer {

        @BindView(R.id.drawer_unified_qr_header)
        lateinit var headerTextView: HtmlTextView

        @BindView(R.id.drawer_unified_qr_address)
        lateinit var addressTextView: TextView

        @BindView(R.id.drawer_unified_qr_address_copy_clickable_area)
        lateinit var addressCopyButton: View

        @BindView(R.id.drawer_unified_qr_invoice)
        lateinit var invoiceTextView: TextView

        @BindView(R.id.drawer_unified_qr_invoice_copy_clickable_area)
        lateinit var invoiceCopyButton: View

        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

        override fun setUp(context: Context, attrs: AttributeSet?) {
            super.setUp(context, attrs)

            StyledStringRes(context, R.string.show_unified_qr_full_content_header, this::learnMore)
                .toCharSequence()
                .let(headerTextView::setText)
        }

        private fun learnMore(link: String) {
            context.openInBrowser(link)
        }

        fun setAddress(address: String) {
            StyledStringRes(context, R.string.show_unified_qr_full_content_address)
                .toCharSequence(address)
                .let(addressTextView::setText)
        }

        fun setOnAddressCopyListener(listener: OnClickListener) {
            addressCopyButton.setOnClickListener(listener)
        }

        fun setOnInvoiceCopyListener(listener: OnClickListener) {
            invoiceCopyButton.setOnClickListener(listener)
        }

        fun setInvoice(invoice: String) {
            StyledStringRes(context, R.string.show_unified_qr_full_content_invoice)
                .toCharSequence(invoice)
                .let(invoiceTextView::setText)
        }

        override val layoutResource: Int
            get() = R.layout.drawer_unified_qr_full_content
    }
}