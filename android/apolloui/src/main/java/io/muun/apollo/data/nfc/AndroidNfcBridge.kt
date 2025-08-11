package io.muun.apollo.data.nfc

import app_provided_data.NfcBridge
import app_provided_data.NfcBridgeResponse
import io.muun.apollo.data.nfc.api.NfcSession
import io.muun.common.utils.Encodings
import timber.log.Timber

/**
 * This class acts as a middleware between Libwallet and the Android NFC API. Enabling to Golang's
 * code to use NFC capabilities of mobile devices that are specific to each mobile platform.
 *
 * Singleton (see {@link DataModule}. Not thread-safe, but this is fine since we shouldn't have
 * concurrent NFC interactions.
 */
class AndroidNfcBridge : NfcBridge {

    private var nfcSession: NfcSession? = null

    fun setUp(nfcSession: NfcSession) {
        this.nfcSession = nfcSession
    }

    override fun transmit(message: ByteArray): NfcBridgeResponse {
        val cardBridgeResponse = NfcBridgeResponse()

        Timber.d("AndroidNfcBridge: transmit ${Encodings.bytesToHex(message)}")

        try {
            nfcSession?.transmit(message)?.let {
                cardBridgeResponse.response = it.response
                cardBridgeResponse.statusCode = it.statusCode
            }

        } catch (e: Exception) {
            Timber.e("AndroidNfcBridge: error sending ${Encodings.bytesToHex(message)}", e)
        }

        return cardBridgeResponse
    }

    fun tearDown() {
        this.nfcSession = null
    }
}
