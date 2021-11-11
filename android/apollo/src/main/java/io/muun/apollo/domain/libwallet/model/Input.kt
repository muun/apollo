package io.muun.apollo.domain.libwallet.model

import io.muun.common.Temporary
import io.muun.common.crypto.hd.MuunInput

class Input(val input: MuunInput) : libwallet.Input {

    override fun userSignature(): ByteArray =
        input.userSignature?.bytes ?: ByteArray(0)

    override fun muunSignature(): ByteArray =
        input.muunSignature?.bytes ?: ByteArray(0)

    override fun muunPublicNonce() =
        input.rawMuunPublicNonce

    override fun address(): Address =
        Address(input.address)

    override fun outPoint(): Outpoint =
        Outpoint(input.prevOut)

    override fun submarineSwapV1(): SubmarineSwapV1? =
        input.submarineSwap?.let { SubmarineSwapV1(it) }

    override fun submarineSwapV2(): SubmarineSwapV2? =
        input.submarineSwapV102?.let { SubmarineSwapV2(it) }

    override fun incomingSwap(): InputIncomingSwap? =
        input.incomingSwap?.let { InputIncomingSwap(it) }

}

