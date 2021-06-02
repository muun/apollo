package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.OperationUri

class MuunUriPaster @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    @BindView(R.id.title)
    lateinit var titleView: TextView

    @BindView(R.id.description)
    lateinit var descriptionView: TextView

    var onSelectListener: (uri: OperationUri) -> Unit = {}

    override fun getLayoutResource() =
        R.layout.muun_uri_paster

    override fun setUp(context: Context?, attrs: AttributeSet?) {
        super.setUp(context, attrs)
        setOnClickListener { uri?.let(onSelectListener) }
    }

    var uri: OperationUri? = null
        set(newUri) {
            if (newUri != null) {
                titleView.text = getUriTitle(newUri)
                descriptionView.text = getUriDescription(newUri)
                visibility = View.VISIBLE

            } else {
                visibility = View.GONE
            }

            field = newUri
        }

    private fun getUriTitle(uri: OperationUri) =
        when {
            uri.lnUrl.isPresent ->
                context.getString(R.string.lnurl_withdraw_uri_paster_title)

            uri.lnInvoice.isPresent ->
                context.getString(R.string.scanqr_drawer_ln_invoice_title)

            uri.bitcoinAddress.isPresent ->
                context.getString(R.string.scanqr_drawer_title)

            else ->
                null // other valid URIs (contact, hardware wallet, etc) are meaningless here
        }

    private fun getUriDescription(uri: OperationUri): CharSequence? {
        val maybeDescription = when {
            uri.lnUrl.isPresent ->
                uri.lnUrl


            uri.lnInvoice.isPresent ->
                uri.lnInvoice

            uri.isAsync ->
                uri.asyncUrlHost.map { context.getString(R.string.scanqr_drawer_no_address, it) }

            else ->
                uri.bitcoinAddress
        }

        return if (maybeDescription.isPresent) {
            maybeDescription.get()
        } else {
            context.getString(R.string.scanqr_drawer_no_address_no_host)
        }
    }
}