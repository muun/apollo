package io.muun.apollo.domain.libwallet

import androidx.annotation.VisibleForTesting
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.common.utils.BitcoinUtils
import libwallet.Libwallet
import libwallet.MuunPaymentURI
import org.bitcoinj.core.NetworkParameters
import java.math.BigDecimal

object BitcoinUri {

    fun generate(
        address: String,
        invoice: String,
        amount: BitcoinAmount?,
        params: NetworkParameters,
    ): String {

        val muunPaymentUri = MuunPaymentURI()
        muunPaymentUri.address = address
        muunPaymentUri.amount = amount?.let { toString(it) }
        muunPaymentUri.invoice = Invoice.parseInvoice(params, invoice)

        return Libwallet.generateBip21Uri(muunPaymentUri)
    }

    @VisibleForTesting
    fun toString(amount: BitcoinAmount) =
        BitcoinUtils.satoshisToBitcoins(amount.inSatoshis)
            .number
            .numberValue(BigDecimal::class.java)
            .toPlainString() // Avoid scientific notation for golang to parse/compare smoothly
}