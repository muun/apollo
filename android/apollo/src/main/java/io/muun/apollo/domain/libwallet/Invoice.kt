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
import libwallet.*
import libwallet.Invoice
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
        forwardingPolicy: ForwardingPolicy,
        amountInSat: Long? = null
    ): String {

        val routeHints = RouteHints()
        routeHints.cltvExpiryDelta = forwardingPolicy.cltvExpiryDelta.toInt()
        routeHints.feeBaseMsat = forwardingPolicy.feeBaseMsat
        routeHints.feeProportionalMillionths = forwardingPolicy.feeProportionalMillionths
        routeHints.pubkey = Encodings.bytesToHex(forwardingPolicy.identityKey)

        val options = InvoiceOptions()

        options.amountSat = amountInSat ?: 0 // Specified amount or amount-less invoice

        val invoice = Libwallet.createInvoice(
            networkParams.toLibwallet(),
            userPrivateKey.toLibwallet(networkParams),
            routeHints,
            options
        )

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
            Sha256Hash.fromBytes(invoice.paymentHash)
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
