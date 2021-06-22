package io.muun.apollo.domain.libwallet.model

import io.muun.common.crypto.hd.MuunInputIncomingSwap
import io.muun.common.utils.Encodings

class InputIncomingSwap(val swap: MuunInputIncomingSwap) : libwallet.InputIncomingSwap {

    override fun htlcTx(): ByteArray =
        swap.htlcTx

    override fun paymentHash256(): ByteArray =
        swap.paymentHash256

    override fun swapServerPublicKey(): String =
        Encodings.bytesToHex(swap.swapServerPublicKey)

    override fun sphinx(): ByteArray =
        swap.sphinx

    override fun expirationHeight(): Long =
        swap.expirationHeight

    override fun collectInSats(): Long =
        swap.collectInSats
}