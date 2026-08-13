package io.muun.apollo.data.nfc.api

import android.nfc.tech.IsoDep
import io.muun.apollo.data.nfc.NfcEmpiricalCache
import io.muun.apollo.data.nfc.NfcSessionImpl

object NfcSessionBuilder {

    fun forTag(tag: IsoDep, empiricalCache: NfcEmpiricalCache): NfcSession {
        return NfcSessionImpl(tag, empiricalCache)
    }

    fun fakeNfcSession(): NfcSession {
        return FakeNfcSession()
    }
}