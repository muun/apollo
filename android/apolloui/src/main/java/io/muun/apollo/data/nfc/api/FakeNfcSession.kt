package io.muun.apollo.data.nfc.api

import io.muun.apollo.data.nfc.CardResponse

class FakeNfcSession : NfcSession {
    override fun connect() {
        // Do Nothing
    }

    override fun transmit(message: ByteArray): CardResponse {
        TODO("Not yet implemented")
    }

    override fun close() {
        // Do Nothing
    }
}
