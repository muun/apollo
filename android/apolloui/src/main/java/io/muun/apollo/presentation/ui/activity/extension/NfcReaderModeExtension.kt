package io.muun.apollo.presentation.ui.activity.extension

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.nfc.api.NfcSessionBuilder
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.di.PerActivity
import timber.log.Timber
import javax.inject.Inject

@PerActivity
class NfcReaderModeExtension @Inject constructor(context: Context) : ActivityExtension(),
    NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    fun enableReaderMode() {
        if (nfcAdapter != null) {
            val options = Bundle()

            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)

            Timber.d("NFC: enableReaderMode")

            // Enable ReaderMode for NFC A cards and disable platform sounds
            nfcAdapter.enableReaderMode(
                activity,
                this,
                NfcAdapter.FLAG_READER_NFC_A
                    or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                    or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                options
            )
        }
    }

    fun disableReaderMode() {
        if (!activity.isFinishing && !activity.isDestroyed) {
            Timber.d("NFC: disableReaderMode")
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        val nfcAtag = IsoDep.get(tag)
        val nfcSession = NfcSessionBuilder.forTag(nfcAtag)
        activity.onNewNfcSession(nfcSession)
    }
}