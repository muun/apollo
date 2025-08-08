package io.muun.apollo.domain.libwallet.model

import io.muun.common.crypto.hd.MuunOutput
import io.muun.common.utils.Encodings

class Outpoint(val output: MuunOutput) : libwallet.Outpoint {

    override fun amount(): Long =
        output.amount

    override fun index(): Long =
        output.index.toLong()

    override fun txId(): ByteArray =
        Encodings.hexToBytes(output.txId)
}