package io.muun.apollo.domain.libwallet

import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.libwallet.errors.InvoiceParsingError
import io.muun.apollo.domain.libwallet.errors.NoInvoicesLeftError
import io.muun.apollo.domain.model.ForwardingPolicy
import io.muun.apollo.domain.model.Sha256Hash
import io.muun.common.api.IncomingSwapJson
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.crypto.hd.PublicKeyPair
import io.muun.common.utils.Encodings
import libwallet.Invoice
import libwallet.InvoiceBuilder
import libwallet.InvoiceSecrets
import libwallet.InvoiceSecretsList
import libwallet.Libwallet
import libwallet.RouteHints
import org.bitcoinj.core.NetworkParameters
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber

object Invoice {

    fun generateSecrets(publicKeyPair: PublicKeyPair): SecretList {

        val networkParameters = publicKeyPair.networkParameters
        val secrets = Libwallet.generateInvoiceSecrets(
            publicKeyPair.userPublicKey.toLibwallet(networkParameters),
            publicKeyPair.muunPublicKey.toLibwallet(networkParameters),
        )

        return SecretList(secrets)
    }

    fun persistSecrets(secrets: SecretList) {
        Libwallet.persistInvoiceSecrets(secrets.list)
    }

    fun generate(
        networkParams: NetworkParameters,
        userPrivateKey: PrivateKey,
        forwardingPolicies: List<ForwardingPolicy>,
        amountInSat: Long? = null
    ): String {

        val builder = InvoiceBuilder()
            .network(networkParams.toLibwallet())
            .userKey(userPrivateKey.toLibwallet(networkParams))

        for (policy in forwardingPolicies) {
            val routeHint = RouteHints()
            routeHint.cltvExpiryDelta = policy.cltvExpiryDelta.toInt()
            routeHint.feeBaseMsat = policy.feeBaseMsat
            routeHint.feeProportionalMillionths = policy.feeProportionalMillionths
            routeHint.pubkey = Encodings.bytesToHex(policy.identityKey)
            builder.addRouteHints(routeHint)
        }

        if (amountInSat != null) {
            builder.amountSat(amountInSat)
        }

        val invoice = builder.build()

        if (invoice.isBlank()) {
            throw NoInvoicesLeftError()
        }

        return invoice

    }

    fun getMetadata(incomingSwap: IncomingSwapJson): String? {
        val paymentHash = Sha256Hash.fromHex(incomingSwap.paymentHashHex)

        return try {
            val invoiceMetadata: String = Libwallet.getInvoiceMetadata(paymentHash.toBytes())

            if (invoiceMetadata.isNotEmpty()) {
                invoiceMetadata

            } else {
                null
            }

        } catch (e: Throwable) {
            Timber.e("Error fetching metadata for invoice. PaymentHash: $paymentHash")
            null
        }
    }

    /**
     * Decode a LN Invoice.
     */
    fun decodeInvoice(params: NetworkParameters, bech32Invoice: String): DecodedInvoice {
        val invoice = parseInvoice(params, bech32Invoice)
        return DecodedInvoice(
            bech32Invoice,
            if (invoice.sats != 0L) invoice.sats else null,
            invoice.description,
            ZonedDateTime.ofInstant(Instant.ofEpochSecond(invoice.expiry), ZoneId.of("Z")),
            Encodings.bytesToHex(invoice.destination),
            Sha256Hash.fromBytes(invoice.paymentHash),
            params
        )
    }

    /**
     * ParseInvoice parses an Invoice from an invoice string and a network.
     * NOTE: this will successfully parse a RAW "<invoice>" AND one with a scheme:
     * "lightning:<invoice>" (uppercase scheme too).
     */
    fun parseInvoice(params: NetworkParameters, bech32Invoice: String): Invoice {
        return try {
            Libwallet.parseInvoice(bech32Invoice, params.toLibwallet())
        } catch (e: Exception) {
            throw InvoiceParsingError(bech32Invoice, e)
        }
    }

    fun isValid(bech32Invoice: String): Boolean {
        return try {
            parseInvoice(Globals.INSTANCE.network, bech32Invoice)
            true
        } catch (e: InvoiceParsingError) {
            false
        }
    }

    class SecretList(val list: InvoiceSecretsList) {

        fun list(): ArrayList<InvoiceSecret> {
            val result = arrayListOf<InvoiceSecret>()

            for (i in 0 until list.length()) {
                result.add(InvoiceSecret(list[i]))
            }

            return result
        }
    }

    class InvoiceSecret(secret: InvoiceSecrets) {

        val paymentHash: ByteArray = secret.paymentHash

        val shortChannelId = secret.shortChanId

        val userPublicKey: PublicKey = Extensions.fromLibwallet(secret.userHtlcKey)

        val muunPublicKey: PublicKey = Extensions.fromLibwallet(secret.muunHtlcKey)

        val identityKey: PublicKey = Extensions.fromLibwallet(secret.identityKey)

    }
}
