package io.muun.apollo.domain.libwallet.model

import io.muun.common.crypto.hd.MuunInputSubmarineSwapV102

class SubmarineSwapV2(val swap: MuunInputSubmarineSwapV102) : libwallet.InputSubmarineSwapV2 {

    override fun blocksForExpiration(): Long =
        swap.numBlocksForExpiration.toLong()

    override fun muunPublicKey(): ByteArray =
        swap.muunPublicKey

    override fun paymentHash256(): ByteArray =
        swap.swapPaymentHash256

    override fun serverPublicKey(): ByteArray =
        swap.swapServerPublicKey

    override fun userPublicKey(): ByteArray =
        swap.userPublicKey

    override fun serverSignature(): ByteArray =
        swap.swapServerSignature?.bytes ?: ByteArray(0)
}