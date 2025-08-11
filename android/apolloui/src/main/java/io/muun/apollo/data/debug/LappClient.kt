package io.muun.apollo.data.debug

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
        WRONG_TAG("wrongTag"),
        UNRESPONSIVE("unresponsive"),
        UNRESPONSIVE_LNURL_SERVICE("unresponsiveLnurlService") // service that generates LNURL
    }

    private val url = Globals.INSTANCE.lappUrl

    private fun executeNow(request: Observable<Response>): Response {
        val response = request.toBlocking().first()!!

        // Simple error handling will do for now
        if (response.code() in 400..599) {
            throw LappClientError(response.message() + ": " + response.bodyAsString())
        }

        return response
    }

    fun getLnInvoice(amountInSats: Int): LnInvoice {

        val request = get("$url/invoice?satoshis=$amountInSats")
        val htmlString = executeNow(request).bodyAsString()

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
     * Return a new withdraw LNURL. Receives a variant param to generate different LNURLs to force
     * different use cases.
     */
    fun generateWithdrawLnUrl(variant: LnUrlVariant = LnUrlVariant.NORMAL): String {

        if (variant == LnUrlVariant.UNRESPONSIVE_LNURL_SERVICE) {
            // Hard-coded lnurl to force "unresponsive service" response
            // Encoded uri is: https://this.domain.does.not.exist.example.com?secret=12345
            return "LNURL1DP68GURN8GHJ7ARGD9EJUER0D4SKJM3WV3HK2UEWDEHHGTN90P5HXAPWV4UXZMTSD3JJUC" +
                "M0D5LHXETRWFJHG0F3XGENGDGQ8EH52"

        }

        // For SLOW ui tests we need the async behavior of the lapp's lnurl withdraw flow. Otherwise
        // Receiving state is never reached (e.g cause the withdraw fullfill request ends after the
        // ln payment is completed).
        val blocking = variant != LnUrlVariant.SLOW
        val request = get("$url/lnurl/withdrawStart?variant=${variant.value}&block=$blocking")
        val response = executeNow(request)

        return response.bodyAsString()
    }

    /**
     * Generate blocks.
     */
    fun generateBlocks(numberOfBlocks: Int) {
        val request = post("$url/generate?blocks=$numberOfBlocks", "")
        executeNow(request)
    }

    /**
     * Drop the last tx from the mempool.
     */
    fun dropLastTxFromMempool() {
        val request = post("$url/dropLastTx", "")
        executeNow(request)
    }

    /**
     * Drop a specific tx from the mempool.
     */
    fun dropTx(txId: String) {
        val request = post("$url/drop?tx=$txId", "")
        executeNow(request)
    }

    /**
     * Undrop a specific tx from the mempool. Can only succeed if tx was dropped using
     * {@link #dropTx()}.
     */
    fun undropTx(txId: String) {
        val request = post("$url/undrop?tx=$txId", "")
        executeNow(request)
    }

    private fun Response.bodyAsString(): String =
        body()!!.string().trim()
}
