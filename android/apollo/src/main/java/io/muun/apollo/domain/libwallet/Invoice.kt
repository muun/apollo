package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.libwallet.errors.NoInvoicesLeftError
import io.muun.apollo.domain.model.ForwardingPolicy
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKeyPair
import io.muun.common.utils.Encodings
import libwallet.InvoiceOptions
import libwallet.InvoiceSecrets
import libwallet.InvoiceSecretsList
import libwallet.Libwallet
import libwallet.RouteHints
import org.bitcoinj.core.NetworkParameters

object Invoice {

    fun generateSecrets(publicKeyPair: PublicKeyPair): SecretList {

        val networkParameters = publicKeyPair.networkParameters
        val secrets = Libwallet.generateInvoiceSecrets(
                LibwalletBridge.toLibwalletModel(publicKeyPair.userPublicKey, networkParameters),
                LibwalletBridge.toLibwalletModel(publicKeyPair.muunPublicKey, networkParameters),
        )

        return SecretList(secrets)
    }

    fun persistSecrets(secrets: SecretList) {
        Libwallet.persistInvoiceSecrets(secrets.list)
    }

    fun generate(
            networkParams: NetworkParameters,
            userPrivateKey: PrivateKey,
            forwardingPolicy: ForwardingPolicy
    ): String {

        val routeHints = RouteHints()
        routeHints.cltvExpiryDelta = forwardingPolicy.cltvExpiryDelta.toInt()
        routeHints.feeBaseMsat = forwardingPolicy.feeBaseMsat
        routeHints.feeProportionalMillionths = forwardingPolicy.feeProportionalMillionths
        routeHints.pubkey = Encodings.bytesToHex(forwardingPolicy.identityKey)

        val options = InvoiceOptions()
        options.amountSat = 0 // no amount invoice

        val invoice = Libwallet.createInvoice(
            LibwalletBridge.toLibwalletModel(networkParams),
            LibwalletBridge.toLibwalletModel(userPrivateKey, networkParams),
            routeHints,
            options
        )

        if (invoice.isBlank()) {
            throw NoInvoicesLeftError()
        }

        return invoice

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

    class InvoiceSecret(private val secret: InvoiceSecrets) {

        val paymentHash = secret.paymentHash

        val shortChannelId = secret.shortChanId

        val userPublicKey = LibwalletBridge.fromLibwalletModel(secret.userHtlcKey)

        val muunPublicKey = LibwalletBridge.fromLibwalletModel(secret.muunHtlcKey)

        val identityKey = LibwalletBridge.fromLibwalletModel(secret.identityKey)

    }
}