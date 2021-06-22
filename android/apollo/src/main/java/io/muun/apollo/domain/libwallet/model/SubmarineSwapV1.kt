package io.muun.apollo.domain.libwallet.model

import io.muun.common.crypto.hd.MuunInputSubmarineSwapV101

class SubmarineSwapV1(val swap: MuunInputSubmarineSwapV101) : libwallet.InputSubmarineSwapV1 {

    override fun paymentHash256(): ByteArray =
        swap.swapPaymentHash256

    override fun refundAddress(): String =
        swap.refundAddress

    override fun lockTime(): Long =
        swap.lockTime

    override fun serverPublicKey(): ByteArray =
        swap.swapServerPublicKey
}