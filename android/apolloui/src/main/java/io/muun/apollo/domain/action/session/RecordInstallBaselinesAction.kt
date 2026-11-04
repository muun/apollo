package io.muun.apollo.domain.action.session

import io.muun.apollo.data.preferences.AndroidIdRepository
import io.muun.apollo.data.preferences.FirstInstallTimeRepository
import io.muun.apollo.domain.copieddevice.DeviceCopyDetector
import io.muun.apollo.domain.copieddevice.DeviceCopyDetector.Signal
import timber.log.Timber
import javax.inject.Inject

/**
 * Record the baselines a device copy is detected against, if absent. Runs during
 * Application#onCreate, so it never throws.
 *
 * A device copied before ANDROID_ID was recorded carries only the first install time. Recording
 * this device's ANDROID_ID then would pin a match for good, so it's recorded only once the first
 * install time matches: until then that signal reads unknown, which never confirms a copy, and
 * a wipe rebases both (see [RebaseInstallBaselinesAction]).
 */
class RecordInstallBaselinesAction @Inject constructor(
    private val firstInstallTimeRepository: FirstInstallTimeRepository,
    private val androidIdRepository: AndroidIdRepository,
    private val deviceCopyDetector: DeviceCopyDetector,
) {

    fun run() {
        record(firstInstallTimeRepository::recordCurrentInstallTimeIfAbsent)
        record {
            if (deviceCopyDetector.firstInstallTimeSignal() == Signal.MATCH) {
                androidIdRepository.recordCurrentAndroidIdIfAbsent()
            }
        }
    }

    private fun record(recordBaseline: () -> Unit) {
        try {
            recordBaseline()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
