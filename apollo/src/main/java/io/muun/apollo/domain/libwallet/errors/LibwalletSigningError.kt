package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet produced an invalid signature"

class LibwalletMismatchSignatureError : MuunError {

    val txId: String

    constructor(txId: String): super(msg) {
        this.txId = txId
        metadata["txId"] = txId
    }

    constructor(txId: String, cause: Throwable): super(msg, cause) {
        this.txId = txId
        metadata["txId"] = txId
    }
}
