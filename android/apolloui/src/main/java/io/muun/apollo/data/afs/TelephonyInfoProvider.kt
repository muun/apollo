package io.muun.apollo.data.afs

import android.content.Context
import android.telephony.TelephonyManager
import io.muun.apollo.data.os.OS
import io.muun.common.Optional
import timber.log.Timber

private const val UNKNOWN = "UNKNOWN"
private const val DATA_UNKNOWN = -1

class TelephonyInfoProvider(context: Context) {

    private val telephonyManager: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Get the ISO country code (2-letter code) for the current network.
     */
    // TODO convert to nullable Kotlin type once all callers are migrated to Kotlin
    val region: Optional<String>
        get() {
            val countryIso = telephonyManager.networkCountryIso

            // This value is unavailable if the user isn't registered to a network, and may be
            // unreliable on CDMA networks.
            return if (countryIso == null || countryIso.isEmpty()) {
                Optional.empty()
            } else
                Optional.of(countryIso.uppercase())
        }

    val dataState: String
        get() = try {
            mapDataState(telephonyManager.dataState)
        } catch (e: Exception) {
            // 1. Docs mention UnsupportedOperationException If the device does not have
            // PackageManager#FEATURE_TELEPHONY_DATA.
            // 2. Undocumented, but we've observed SecurityException: Requires READ_PHONE_STATE in
            // the wild, in some Samsung Android 5 devices. So, catching that as well.

            // Using our own custom DATA_UNKNOWN instead of TelephonyManager.DATA_UNKNOWN as it was
            // only added in API 29.
            mapDataState(DATA_UNKNOWN)
        }

    val simRegion: String
        get() {
            return telephonyManager.simCountryIso
        }

    // TODO this should probably have unit tests. Specially for the simSlots > 1 but
    //  supportsGetSimStateWithSlotIndex = false
    val simStates: List<String>
        get() {
            val simSlots = simSlots
            if (simSlots == 1) {
                return listOf(mapSimState(telephonyManager.simState))
            } else {
                if (OS.supportsGetSimStateWithSlotIndex()) {
                    return (0 until simSlots)
                        .toList()
                        .map { mapSimState(telephonyManager.getSimState(it)) }
                } else {
                    // we have more than 1 sim but telephonyManager API doesn't let use query them
                    val simSates = mutableListOf(mapSimState(telephonyManager.simState))
                    val unknowns = (0 until simSlots)
                        .toList()
                        .map { UNKNOWN }
                    simSates.addAll(unknowns)
                    return simSates
                }
            }
        }

    val simOperatorId: String
        get() {
            return telephonyManager.simOperator
        }

    val mobileNetworkId: String
        get() {
            return telephonyManager.networkOperator
        }

    val mobileRoaming: Boolean
        get() {
            return telephonyManager.isNetworkRoaming
        }

    val mobileDataStatus: Int
        get() {
            if (OS.supportsIsDataEnabled()) {
                return try {
                    if (telephonyManager.isDataEnabled) 1 else 0
                } catch (e: SecurityException) {
                    -2
                }
            }
            return -1
        }

    val mobileRadioType: Int
        get() {
            return telephonyManager.phoneType
        }

    val mobileDataActivity: Int
        get() {
            return telephonyManager.dataActivity
        }

    val regionList: List<String>
        get() {
            if (OS.supportsGetNetworkCountryIsoWithSlotIndex()) {
                return try {
                    (0 until simSlots)
                        .toList()
                        .map {
                            telephonyManager.getNetworkCountryIso(it)
                        }
                } catch (e: Exception) {
                    //just in unlikely case that SimsLots has a higher that the available slotIndex
                    Timber.e(e, "SimsLots has a higher that the available slotIndex")
                    emptyList()
                }
            }
            return emptyList()
        }


    private fun mapDataState(dataState: Int): String {
        when (dataState) {
            TelephonyManager.DATA_DISCONNECTED -> return "DATA_DISCONNECTED"
            TelephonyManager.DATA_CONNECTING -> return "DATA_CONNECTING"
            TelephonyManager.DATA_CONNECTED -> return "DATA_CONNECTED"
            TelephonyManager.DATA_SUSPENDED -> return "DATA_SUSPENDED"
            TelephonyManager.DATA_DISCONNECTING -> return "DATA_DISCONNECTING"
            TelephonyManager.DATA_HANDOVER_IN_PROGRESS -> return "DATA_HANDOVER_IN_PROGRESS"
            TelephonyManager.DATA_UNKNOWN -> return "DATA_UNKNOWN"
        }
        return UNKNOWN
    }

    private val simSlots: Int
        get() {
            return if (OS.supportsGetActiveModemCount()) {
                telephonyManager.activeModemCount
            } else if (OS.supportsGetPhoneCount()) {
                telephonyManager.phoneCount
            } else {
                1
            }
        }

    private fun mapSimState(simState: Int): String {
        when (simState) {
            TelephonyManager.SIM_STATE_ABSENT -> return "SIM_STATE_ABSENT"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> return "SIM_STATE_NETWORK_LOCKED"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> return "SIM_STATE_PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> return "SIM_STATE_PUK_REQUIRED"
            TelephonyManager.SIM_STATE_READY -> return "SIM_STATE_READY"
            TelephonyManager.SIM_STATE_NOT_READY -> return "SIM_STATE_NOT_READY"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> return "SIM_STATE_PERM_DISABLED"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> return "SIM_STATE_CARD_IO_ERROR"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> return "SIM_STATE_CARD_RESTRICTED"
            TelephonyManager.SIM_STATE_UNKNOWN -> return "SIM_STATE_UNKNOWN"
        }
        return UNKNOWN
    }
}