package io.muun.apollo.presentation.ui.debug

import io.muun.apollo.BuildConfig
import io.muun.apollo.data.external.Globals
import io.muun.common.utils.LnInvoice
import okhttp3.Response
import rx.Observable


class LappClient : SimpleHttpClient() {

    private val url = BuildConfig.LAPP_URL

    private fun executeNow(request: Observable<Response>): String {
        val response = request.toBlocking().first()!!
        return response.body()!!.string()!!
    }

    fun getLnInvoice(amountInSats: Int): LnInvoice {

        val request = get("$url/invoice?satoshis=$amountInSats")
        val htmlString = executeNow(request)

        val invoiceString = htmlString
            .substringBefore("</span>")
            .substringAfter("<span>")

        return LnInvoice.decode(Globals.INSTANCE.network, invoiceString)
    }

    /**
     * Receive BTC on the given address.
     */
    fun receiveBtc(bitcoin: Double, targetAddress: String) {
        val request = post("$url/send?address=$targetAddress&amount=$bitcoin", "")
        executeNow(request)
    }

    /**
     * Receive BTC using Lightning Network.
     */
    fun receiveBtcViaLN(invoice: String, amountInSat: Long? = null, turboChannels: Boolean) {
        // We need to signal lapp that we want payment to be processed async or not depending on
        // whether the payment will be made using turbo channels (0-conf/sync) or not
        val async = if (!turboChannels) "&async=1" else ""

        // Amount must not be specified when paying a non-zero amount invoice (e.g invoice
        // with amount). Otherwise an error occurs.
        val withAmount = if (amountInSat != null) "&satoshis=$amountInSat" else ""

        val request = post("$url/payInvoice?invoice=$invoice$withAmount$async", "")
        executeNow(request)
    }

    /**
     * Generate blocks.
     */
    fun generateBlocks(numberOfBlocks: Int) {
        val request = post("$url/generate?blocks=$numberOfBlocks", "")
        executeNow(request)
    }
}
