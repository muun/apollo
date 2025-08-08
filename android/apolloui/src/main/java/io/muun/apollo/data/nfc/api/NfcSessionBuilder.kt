package io.muun.apollo.data.nfc.api

import android.nfc.tech.IsoDep
import io.muun.apollo.data.nfc.NfcSessionImpl

object NfcSessionBuilder {

    fun forTag(tag: IsoDep): NfcSession {
        return NfcSessionImpl(tag)
    }

    fun fakeNfcSession(): NfcSession {
        return FakeNfcSession()
    }
}