package io.muun.apollo.domain.libwallet

import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.utils.Encodings
import libwallet.HDPrivateKey
import libwallet.HDPublicKey
import libwallet.Libwallet
import libwallet.Network
import libwallet.UserActivatedFeature
import org.bitcoinj.core.NetworkParameters
import org.javamoney.moneta.Money
import java.math.BigDecimal
import javax.money.MonetaryAmount

fun NetworkParameters.toLibwallet(): Network =
    when {
        NetworkParameters.ID_MAINNET == id -> Libwallet.mainnet()
        NetworkParameters.ID_REGTEST == id -> Libwallet.regtest()
        else -> Libwallet.testnet()
    }

fun PrivateKey.toLibwallet(params: NetworkParameters): HDPrivateKey =
    HDPrivateKey(serializeBase58(), absoluteDerivationPath, params.toLibwallet())

fun PublicKey.toLibwallet(params: NetworkParameters): HDPublicKey =
    HDPublicKey(serializeBase58(), absoluteDerivationPath, params.toLibwallet())

/**
 * Would LOVE to override equals but we can't override methods with an extension function :'(.
 */
fun UserActivatedFeature.isEqualTo(other: Any?): Boolean {
    if (this === other) {
        return true
    }

    if (javaClass != other?.javaClass) {
        return false
    }

    other as UserActivatedFeature

    return backendFeature().equals(other.backendFeature())
}

object Extensions {

    // If PublicKey was Kotlin class we could add extensions fun to its companion
    // TODO: mv this to an extension fun when PublicKey is migrated to Kotlin
    fun fromLibwallet(pubKey: HDPublicKey): PublicKey =
        PublicKey.deserializeFromBase58(pubKey.path, pubKey.string())

    fun fromLibwallet(privKey: HDPrivateKey): PrivateKey =
        PrivateKey.deserializeFromBase58(privKey.path, privKey.string())

}

fun newop.MonetaryAmount.adapt(): MonetaryAmount =
    Money.of(BigDecimal(this.valueAsString()), this.currency)

fun libwallet.Invoice.remainingMillis(): Long {
    val expirationTimeInMillis = this.expiry * 1000 // expiry contains expiry date in epoch seconds
    return expirationTimeInMillis - System.currentTimeMillis()
}

fun libwallet.Invoice.destinationPubKey(): String =
    Encodings.bytesToHex(this.destination)
