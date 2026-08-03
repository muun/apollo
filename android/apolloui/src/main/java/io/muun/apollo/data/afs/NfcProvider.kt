package io.muun.apollo.data.afs

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import io.muun.apollo.data.nfc.NfcEmpiricalCache
import io.muun.apollo.data.os.OS
import timber.log.Timber

class NfcProvider(
    private val context: Context,
    private val extendedApduProbe: NfcExtendedApduProbe,
    private val empiricalCache: NfcEmpiricalCache,
) {

    // We could use getSystemService(Context.NFC_SERVICE) as? NfcManager but it's just a wrapper
    // of this.
    private val nfcAdapter: NfcAdapter? = try {
        NfcAdapter.getDefaultAdapter(context)
    } catch (e: Throwable) {
        null
    }

    val hasNfcFeature: Boolean
        get() {
            val packageManager = context.packageManager
            return packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        }

    val hasNfcAdapter: Boolean
        get() = nfcAdapter != null

    val isNfcEnabled: Boolean
        get() = nfcAdapter != null && nfcAdapter.isEnabled

    val hasNfcHostCardEmulation: Int
        get() = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_NFC_HOST_CARD_EMULATION
        ).toTristate()

    val hasNfcOffHostCardEmulationUicc: Int
        get() = if (OS.supportsNfcOffHostCardEmulation()) {
            context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC
            ).toTristate()
        } else {
            Constants.INT_UNKNOWN
        }

    val hasNfcOffHostCardEmulationEse: Int
        get() = if (OS.supportsNfcOffHostCardEmulation()) {
            context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE
            ).toTristate()
        } else {
            Constants.INT_UNKNOWN
        }

    val nfcExtendedApduSupportedEmpirical: Int
        get() = empiricalCache.extendedApduSupported.toTristate()

    val nfcMaxTransceiveLengthEmpirical: Int
        get() = empiricalCache.maxTransceiveLength ?: Constants.INT_UNKNOWN

    val nfcConfigFilesPresent: List<String>
        get() = extendedApduProbe.configFileScan.filesPresent

    val nfcChipIdentifier: String
        get() = extendedApduProbe.configFileScan.chipIdentifier

    val nfcConfigFileHash: String
        get() = extendedApduProbe.configFileScan.contentHash

    val nfcExtendedApduSupportedReflected: Int
        get() = extendedApduProbe.reflectionScan.extendedApduSupported.toTristate()

    val nfcMaxTransceiveLengthReflected: Int
        get() = extendedApduProbe.reflectionScan.maxTransceiveLength ?: Constants.INT_UNKNOWN

    val nfcReflectionFailureReason: String
        get() = extendedApduProbe.reflectionScan.failureReason

    private fun Boolean?.toTristate(): Int = when (this) {
        null -> Constants.INT_UNKNOWN
        false -> Constants.INT_ABSENT
        true -> Constants.INT_PRESENT
    }

    private fun Boolean.toTristate(): Int = if (this) Constants.INT_PRESENT else Constants.INT_ABSENT

    /**
     * Location of the antenna in millimeters. 0 is the bottom-left when the user is facing the
     * screen and the device orientation is Portrait.
     */
    val nfcAntennaPosition: List<Pair<Float, Float>>
        get() {
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

    val deviceSizeInMm: Pair<Int, Int>?
        get() {
            try {
                return if (OS.supportsAvailableNfcAntennas()) {
                    // nfcAntennaInfo was added alongside with availableNfcAntennas
                    nfcAdapter?.nfcAntennaInfo?.let { nfcAntennaInfo ->
                        Pair(nfcAntennaInfo.deviceWidth, nfcAntennaInfo.deviceHeight)
                    }

                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.i("Error while reading NFC data from NFC compat device: ${e.message}")
                return null
            }
        }

    val isDeviceFoldable: Boolean?
        get() {
            try {
                return if (OS.supportsAvailableNfcAntennas()) {
                    // nfcAntennaInfo was added alongside with availableNfcAntennas
                    nfcAdapter?.nfcAntennaInfo?.isDeviceFoldable

                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.i("Error while reading NFC data from NFC compat device: ${e.message}")
                return null
            }
        }
}