package io.muun.apollo.data.nfc.api

import io.muun.apollo.data.nfc.CardResponse

/**
 * Dummy implementation of NfcSession. Meant to enable using Libwallet's MockNfcBridge to use
 * a mock security card to develop and test in emulators and have security cards ui tests.
 */
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
