package io.muun.apollo.data.nfc.api

import io.muun.apollo.data.nfc.CardResponse

interface NfcSession {

    fun connect()
    fun transmit(message: ByteArray): CardResponse
    fun close()
}