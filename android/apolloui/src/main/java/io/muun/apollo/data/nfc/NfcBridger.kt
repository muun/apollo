package io.muun.apollo.data.nfc

import io.muun.apollo.data.nfc.api.NfcSession
import timber.log.Timber

class NfcBridger(
    private val androidNfcBridge: AndroidNfcBridge,
    private val nfcSession: NfcSession,
) {

    /**
     * Connect the NfcSession (wraps android NFC Tag API) and hand it to nfcBridge to make nfc
     * calls. Single use: pair with [tearDownBridge], which closes the session.
     */
    fun setupBridge() {
        nfcSession.connect()
        androidNfcBridge.setUp(nfcSession)
    }

    /**
     * Release NfcSession after use.
     */
    fun tearDownBridge() {
        androidNfcBridge.tearDown()

        try {
            nfcSession.close()
        } catch (e: Exception) {
            Timber.e(e, "Failed to close the nfc session")
        }
    }
}