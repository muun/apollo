package io.muun.apollo.domain.libwallet

import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.crypto.hd.MuunInput
import io.muun.common.crypto.hd.MuunInputSubmarineSwapV101
import io.muun.common.crypto.hd.MuunInputSubmarineSwapV102
import io.muun.common.crypto.hd.MuunOutput
import io.muun.common.utils.Encodings

class Input(val input: MuunInput): libwallet.Input {

    override fun userSignature() =
            input.userSignature?.bytes ?: ByteArray(0)

    override fun muunSignature() =
            input.muunSignature?.bytes ?: ByteArray(0)

    override fun address() =
            Address(input.address)

    override fun outPoint() =
            Outpoint(input.prevOut)

    override fun submarineSwapV1() =
            input.submarineSwap?.let { SubmarineSwapV1(it) }

    override fun submarineSwapV2() =
            input.submarineSwapV102?.let { SubmarineSwapV2(it) }
}

class Address(val address: MuunAddress): libwallet.MuunAddress {

    override fun derivationPath() =
            address.derivationPath

    override fun version() =
            address.version.toLong()

    override fun address() =
            address.address

}

class Outpoint(val output: MuunOutput): libwallet.Outpoint {

    override fun amount() =
            output.amount

    override fun index() =
            output.index.toLong()

    override fun txId() =
            Encodings.hexToBytes(output.txId)

}

class SubmarineSwapV1(val swap: MuunInputSubmarineSwapV101): libwallet.InputSubmarineSwapV1 {

    override fun paymentHash256() =
            swap.swapPaymentHash256

    override fun refundAddress() =
            swap.refundAddress

    override fun lockTime() =
            swap.lockTime

    override fun serverPublicKey() =
            swap.swapServerPublicKey
}

class SubmarineSwapV2(val swap: MuunInputSubmarineSwapV102): libwallet.InputSubmarineSwapV2 {

    override fun blocksForExpiration() =
            swap.numBlocksForExpiration.toLong()

    override fun muunPublicKey() =
            swap.muunPublicKey

    override fun paymentHash256() =
            swap.swapPaymentHash256

    override fun serverPublicKey() =
            swap.swapServerPublicKey

    override fun userPublicKey() =
            swap.userPublicKey

    override fun serverSignature() =
            swap.swapServerSignature?.bytes ?: ByteArray(0)
}