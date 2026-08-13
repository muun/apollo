package io.muun.apollo.data.nfc

import android.nfc.tech.IsoDep
import io.muun.apollo.data.nfc.api.NfcSession
import io.muun.common.utils.Encodings
import timber.log.Timber
import java.io.IOException

/**
 * Provides access to the Android NFC API, in particular to ISO-DEP (ISO 14443-4) properties and
 * I/ O operations on a Tag. Wraps and enhances Android's IsoDep class.
 */
internal class NfcSessionImpl(
    private val nfcAtag: IsoDep,
    private val empiricalCache: NfcEmpiricalCache,
) : NfcSession {

    override fun connect() {
        nfcAtag.connect()

        val maxLen = nfcAtag.maxTransceiveLength
        val supportsExtended = nfcAtag.isExtendedLengthApduSupported
        Timber.i("maxTransceiveLength: $maxLen")
        Timber.i("isExtendedLengthApduSupported: $supportsExtended")
        Timber.d("historicalBytes: ${Encodings.bytesToHex(nfcAtag.historicalBytes)}")
        Timber.d("Timeout: ${nfcAtag.timeout}")

        empiricalCache.update(supportsExtended, maxLen)

        nfcAtag.timeout *= 5
    }

    override fun close() {
        nfcAtag.close()
    }

    /**
     * Sends a message to the smart card and returns the response with its status code.
     * The response is split into the actual data and the status word (last 2 bytes).
     */
    override fun transmit(message: ByteArray): CardResponse {
        val resp: ByteArray
        try {
            resp = transceive(message)

        } catch (e: Exception) {
            throw RuntimeException("Error transmitting message to the card", e)
        }

        if (resp.size < 2) {
            throw IllegalArgumentException("Invalid response from card: too short to contain status word")
        }

        // Extract the status word (last 2 bytes)
        val statusCode =
            ((resp[resp.size - 2].toInt() and 0xFF) shl 8) or (resp[resp.size - 1].toInt() and 0xFF)

        // Extract the response data (excluding the status word)
        val response = resp.copyOfRange(0, resp.size - 2)

        return CardResponse(response, statusCode)
    }

    @Throws(CommunicationException::class)
    private fun transceive(command: ByteArray): ByteArray {
        var response: ByteArray = byteArrayOf()

        try {
            if (nfcAtag.isConnected) {
                Timber.d("Sending: ${Encodings.bytesToHex(command)}")
                Timber.d("Length: ${command.size}")
                response = nfcAtag.transceive(command)
            }

        } catch (e: IOException) {
            throw CommunicationException(e.message, e)
        }

        Timber.d("Received: " + response.let { Encodings.bytesToHex(it) })

        return response
    }
}