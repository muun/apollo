package io.muun.apollo.presentation.ui.utils

import android.content.Context
import android.text.TextUtils
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_OPEN_WEB
import io.muun.apollo.domain.model.SubmarineSwapReceiver
import io.muun.apollo.presentation.app.di.PerApplication
import io.muun.apollo.presentation.ui.view.RichText
import io.muun.common.bitcoinj.NetworkParametersHelper
import org.bitcoinj.core.NetworkParameters
import javax.inject.Inject

@PerApplication
class LinkBuilder @Inject internal constructor(
    private val context: Context,
    private val network: NetworkParameters,
    private val analytics: Analytics,
) {

    companion object {
        private fun isTestnet(network: NetworkParameters): Boolean {
            return NetworkParametersHelper.isTestingNetwork(network)
        }
    }

    /**
     * Create a RichText containing a clickable link to a transaction in a block explorer.
     */
    fun transactionLink(txId: String): RichText {
        return createLink(txId, rawTransactionLink(txId), "block_explorer_tx")
    }

    /**
     * Create a plain-text link to a transaction in a block explorer.
     */
    fun rawTransactionLink(txId: String): String {
        val urlRoot = if (isTestnet(network)) {
            "https://mempool.space/testnet/tx/"
        } else {
            "https://mempool.space/tx/"
        }

        return "$urlRoot$txId?mode=details"
    }

    /**
     * Create a RichText containing a clickable link to an address in a block explorer.
     */
    fun addressLink(address: String): RichText {
        val urlRoot = if (isTestnet(network)) {
            "https://mempool.space/testnet/address/"
        } else {
            "https://mempool.space/address/"
        }

        return createLink(address, urlRoot + address, "block_explorer_address")
    }

    /**
     * Create a RichText containing a clickable link to a lightning node in an explorer.
     */
    fun lightningNodeLink(receiver: SubmarineSwapReceiver, linkText: String): RichText {
        val urlRoot = if (isTestnet(network)) {
            "https://1ml.com/testnet/node/"
        } else {
            "https://1ml.com/node/"
        }

        return createLink(linkText, urlRoot + receiver.publicKey, "node_explorer")
    }

    /**
     * Create a RichText containing a clickable link to the recovery tool repository.
     */
    fun recoveryToolLink(): RichText {
        val url = "https://github.com/muun/recovery"
        return createLink(url, url, "recovery_tool")
    }

    private fun getLnLinkText(receiver: SubmarineSwapReceiver): String? {
        return if (!TextUtils.isEmpty(receiver.alias)) {
            receiver.alias
        } else {
            receiver.formattedDestination
        }
    }

    /**
     * Create a RichText containing a clickable link.
     */
    private fun createLink(text: String, url: String, trackingName: String): RichText {
        return RichText(text).setLink {
            context.openInBrowser(url)
            analytics.report(E_OPEN_WEB(trackingName, url))
        }
    }
}