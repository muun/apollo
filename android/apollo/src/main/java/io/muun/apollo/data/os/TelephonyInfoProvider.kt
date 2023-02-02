package io.muun.apollo.data.os

import android.content.Context
import android.telephony.TelephonyManager
import io.muun.common.Optional
import javax.inject.Inject

private const val UNKNOWN = "UNKNOWN"

// TODO open to make tests work with mockito. We should probably move to mockK
open class TelephonyInfoProvider @Inject constructor(private val context: Context) {

    private val telephonyManager: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Get the ISO country code (2-letter code) for the current network.
     */
    // TODO convert to nullable Kotlin type once all callers are migrated to Kotlin
    // TODO open to make tests work with mockito. We should probably move to mockK
    open val region: Optional<String>
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
        get() {
            return mapDataState(telephonyManager.dataState)
        }

    // TODO this should probably have unit tests. Specially for the simSlots > 1 but
    //  supportsGetSimStateWithSlotIndex = false
    fun getSimStates(): List<String> {

        val simSlots = simSlots

        if (simSlots == 1) {
            return listOf(mapSimState(telephonyManager.simState))
        } else {

            if (OS.supportsGetSimStateWithSlotIndex()) {
                return (1..simSlots)
                    .toList()
                    .map { mapSimState(telephonyManager.getSimState(it)) }
            } else {
                // we have more than 1 sim but telephonyManager API doesn't let use query them

                val simSates = mutableListOf(mapSimState(telephonyManager.simState))

                val unknowns = (1 until simSlots)
                    .toList()
                    .map { UNKNOWN }

                simSates.addAll(unknowns)

                return simSates
            }
        }
    }

    private fun mapDataState(dataState: Int): String {
        when (dataState) {
            TelephonyManager.DATA_DISCONNECTED -> return "DATA_DISCONNECTED"
            TelephonyManager.DATA_CONNECTING -> return "DATA_CONNECTING"
            TelephonyManager.DATA_CONNECTED -> return "DATA_CONNECTED"
            TelephonyManager.DATA_SUSPENDED -> return "DATA_SUSPENDED"
            TelephonyManager.DATA_DISCONNECTING -> return "DATA_DISCONNECTING"
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