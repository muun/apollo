package io.muun.apollo.presentation.ui.show_qr

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.DrawerUnifiedQrFullContentBinding
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.openInBrowser
import io.muun.apollo.presentation.ui.view.BindingInflater
import io.muun.apollo.presentation.ui.view.DrawerDialogFragment
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

        // MuunActionDrawer fixes the binding type to ViewBinding since its subclasses inflate
        // their own layouts, so we narrow it here.
        private val contentBinding: DrawerUnifiedQrFullContentBinding
            get() = binding as DrawerUnifiedQrFullContentBinding

        private val headerTextView get() = contentBinding.drawerUnifiedQrHeader
        private val addressTextView get() = contentBinding.drawerUnifiedQrAddress
        private val addressCopyButton: View
            get() = contentBinding.drawerUnifiedQrAddressCopyClickableArea
        private val invoiceTextView get() = contentBinding.drawerUnifiedQrInvoice
        private val invoiceCopyButton: View
            get() = contentBinding.drawerUnifiedQrInvoiceCopyClickableArea

        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

        override fun bindingInflater(): BindingInflater<ViewBinding> {
            return DrawerUnifiedQrFullContentBinding::inflate
        }

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
    }
}