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
     * Generate blocks.
     */
    fun generateBlocks(numberOfBlocks: Int) {
        val request = post("$url/generate?blocks=$numberOfBlocks", "")
        executeNow(request)
    }

}
