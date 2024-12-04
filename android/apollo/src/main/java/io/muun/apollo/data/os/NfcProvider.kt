package io.muun.apollo.data.os

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import timber.log.Timber
import javax.inject.Inject

class NfcProvider @Inject constructor(private val context: Context) {

    // We could use getSystemService(Context.NFC_SERVICE) as? NfcManager but it's just a wrapper
    // of this.
    private val nfcAdapter: NfcAdapter? = try {
        NfcAdapter.getDefaultAdapter(context)
    } catch (e: Throwable) {
        null
    }

    fun hasNfcFeature(): Boolean {
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
    }

    val hasNfcAdapter: Boolean
        get() = nfcAdapter != null

    val isNfcEnabled: Boolean
        get() = nfcAdapter != null && nfcAdapter.isEnabled

    fun getNfcAntennaPosition(): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()

        try {
            if (OS.supportsAvailableNfcAntennas()) {
                val antennas = nfcAdapter?.nfcAntennaInfo?.availableNfcAntennas.orEmpty()
                for (antenna in antennas) {
                    result.add(Pair(antenna.locationX.toFloat(), antenna.locationY.toFloat()))
                }
            }
        } catch (e: Exception) {
            Timber.i("Error while reading NFC data from NFC compat device: ${e.message}")
        }

        return result
    }
}