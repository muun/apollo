package io.muun.apollo.data.nfc

import io.muun.apollo.data.nfc.api.NfcSession

class NfcBridger(
    private val androidNfcBridge: AndroidNfcBridge,
    private val nfcSession: NfcSession,
) {

    /**
     * Set up NfcSession (wraps android NFC Tag API) for nfcBridge to make nfc calls.
     */
    fun setupBridge() {
        androidNfcBridge.setUp(nfcSession)
    }

    /**
     * Release NfcSession after use.
     */
    fun tearDownBridge() {
        androidNfcBridge.tearDown()
    }
}