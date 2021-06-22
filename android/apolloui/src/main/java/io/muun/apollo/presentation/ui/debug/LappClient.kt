package io.muun.apollo.presentation.ui.debug

import io.muun.apollo.BuildConfig
import io.muun.apollo.data.external.Globals
import io.muun.common.utils.LnInvoice
import okhttp3.Response
import rx.Observable


class LappClient : SimpleHttpClient() {

    enum class LnUrlVariant(val value: String) {
        NORMAL("normal"),
        SLOW("slow"),
        INVOICE_EXPIRES("expires"),
        FAILS("fails"),
        NO_BALANCE("noBalance"),
        EXPIRED_LNURL("expiredLnurl"),
        NO_ROUTE("noRoute"),
        WRONT_TAG("wrongTag"),
        UNRESPONSIVE("unresponsive")
    }

    private val url = BuildConfig.LAPP_URL

    private fun executeNow(request: Observable<Response>): String {
        val response = request.toBlocking().first()!!
        return response.body()!!.string()
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
     * Return a new withdraw LNURL. Receives a variant param to generate different LNRULs to force
     * different use cases.
     */
    fun generateWithdrawLnUrl(variant: LnUrlVariant = LnUrlVariant.NORMAL): String {

        if (variant == LnUrlVariant.UNRESPONSIVE) {
            // Hard-coded lnurl to force "unresponsive service" response
            return "LNURL1DP68GURN8GHJ7ARGD9EJUER0D4SKJM3WV3HK2UEWDEHHGTN90P5HXAPWV4UXZMTSD3JJUC" +
                "M0D5LHXETRWFJHG0F3XGENGDGQ8EH52"
        }

        val request = get("$url/lnurl/withdrawStart?variant=${variant.value}")
        val response = executeNow(request)

        return response.trim()
    }

    /**
     * Generate blocks.
     */
    fun generateBlocks(numberOfBlocks: Int) {
        val request = post("$url/generate?blocks=$numberOfBlocks", "")
        executeNow(request)
    }
}
