package io.muun.apollo.domain.libwallet

import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import libwallet.HDPrivateKey
import libwallet.HDPublicKey
import libwallet.Libwallet
import libwallet.Network
import org.bitcoinj.core.NetworkParameters

fun NetworkParameters.toLibwalletModel(): Network =
    when {
        NetworkParameters.ID_MAINNET == id -> Libwallet.mainnet()
        NetworkParameters.ID_REGTEST == id -> Libwallet.regtest()
        else -> Libwallet.testnet()
    }

fun PrivateKey.toLibwalletModel(params: NetworkParameters): HDPrivateKey =
    HDPrivateKey(
        serializeBase58(),
        absoluteDerivationPath,
        params.toLibwalletModel()
    )

fun PublicKey.toLibwalletModel(params: NetworkParameters): HDPublicKey =
    HDPublicKey(
        serializeBase58(),
        absoluteDerivationPath,
        params.toLibwalletModel()
    )

object Extensions {

    // If PublicKey was Kotlin class we could add extensions fun to its companion
    // TODO: mv this to an extension fun when PublicKey is migrated to Kotlin
    fun fromLibwalletModel(pubKey: HDPublicKey): PublicKey =
        PublicKey.deserializeFromBase58(pubKey.path, pubKey.string())
}
