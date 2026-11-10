package io.muun.apollo.domain.copieddevice

import io.muun.apollo.data.preferences.AndroidIdRepository
import io.muun.apollo.data.preferences.FirstInstallTimeRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Tells whether this install's preferences were restored from elsewhere: onto another device by
 * an OEM device-to-device copy, or onto this one after a factory reset. It compares what was
 * recorded against what this install reports.
 *
 * Each signal can also change on its own (an OEM restore onto the same device brings a new
 * install time but the same ANDROID_ID), so only both disagreeing at once confirms a copy. A
 * value missing or unreadable on either side is never a disagreement.
 *
 * Nothing acts on a copy yet: it ships ahead of that change. RecordInstallBaselinesAction only
 * reads the first install time signal to decide whether to record the ANDROID_ID.
 */
class DeviceCopyDetector @Inject constructor(
    private val firstInstallTimeRepository: FirstInstallTimeRepository,
    private val androidIdRepository: AndroidIdRepository,
) {

    enum class Signal {
        MATCH,
        MISMATCH,
        UNKNOWN,
    }

    data class Signals(
        val firstInstallTime: Signal,
        val androidId: Signal,
    ) {
        val isConfirmedCopy: Boolean
            get() = firstInstallTime == Signal.MISMATCH && androidId == Signal.MISMATCH
    }

    fun signals(): Signals {
        return Signals(
            firstInstallTime = firstInstallTimeSignal(),
            androidId = compare(
                androidIdRepository::recordedAndroidId,
                androidIdRepository::currentAndroidId,
            ),
        )
    }

    fun firstInstallTimeSignal(): Signal {
        return compare(
            firstInstallTimeRepository::recordedInstallTimeInMs,
            firstInstallTimeRepository::currentInstallTimeInMs,
        )
    }

    private fun <T : Any> compare(recorded: () -> T?, current: () -> T?): Signal {
        return try {
            val recordedValue = recorded() ?: return Signal.UNKNOWN
            val currentValue = current() ?: return Signal.UNKNOWN

            if (recordedValue == currentValue) {
                Signal.MATCH
            } else {
                Signal.MISMATCH
            }

        } catch (e: Exception) {
            Timber.e(e)
            Signal.UNKNOWN
        }
    }
}
