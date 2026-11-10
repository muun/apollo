package io.muun.apollo.domain.copieddevice

import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.preferences.AndroidIdRepository
import io.muun.apollo.data.preferences.FirstInstallTimeRepository
import io.muun.apollo.domain.copieddevice.DeviceCopyDetector.Signal
import io.muun.apollo.domain.copieddevice.DeviceCopyDetector.Signals
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DeviceCopyDetectorTest : BaseTest() {

    private companion object {
        const val AN_INSTALL_TIME = 1_700_000_000_000L
        const val A_LATER_INSTALL_TIME = 1_800_000_000_000L
        const val AN_ANDROID_ID = "3f2a9c1e7b4d6a05"
        const val ANOTHER_ANDROID_ID = "1b4e28ba2fa1aa2b"
    }

    private val firstInstallTimeRepository: FirstInstallTimeRepository = mockk()
    private val androidIdRepository: AndroidIdRepository = mockk()

    private val detector = DeviceCopyDetector(firstInstallTimeRepository, androidIdRepository)

    @Test
    fun `should confirm a copy when both signals disagree`() {
        // GIVEN
        installTimes(recorded = AN_INSTALL_TIME, current = A_LATER_INSTALL_TIME)
        androidIds(recorded = AN_ANDROID_ID, current = ANOTHER_ANDROID_ID)

        // WHEN
        val signals = detector.signals()

        // THEN
        assertThat(signals).isEqualTo(Signals(Signal.MISMATCH, Signal.MISMATCH))
        assertThat(signals.isConfirmedCopy).isTrue()
    }

    @Test
    fun `should not confirm a copy when only the install time disagrees`() {
        // GIVEN the shape an OEM restore onto the same device leaves behind.
        installTimes(recorded = AN_INSTALL_TIME, current = A_LATER_INSTALL_TIME)
        androidIds(recorded = AN_ANDROID_ID, current = AN_ANDROID_ID)

        // WHEN
        val signals = detector.signals()

        // THEN
        assertThat(signals).isEqualTo(Signals(Signal.MISMATCH, Signal.MATCH))
        assertThat(signals.isConfirmedCopy).isFalse()
    }

    @Test
    fun `should not confirm a copy when only the ANDROID_ID disagrees`() {
        // GIVEN
        installTimes(recorded = AN_INSTALL_TIME, current = AN_INSTALL_TIME)
        androidIds(recorded = AN_ANDROID_ID, current = ANOTHER_ANDROID_ID)

        // WHEN
        val signals = detector.signals()

        // THEN
        assertThat(signals).isEqualTo(Signals(Signal.MATCH, Signal.MISMATCH))
        assertThat(signals.isConfirmedCopy).isFalse()
    }

    @Test
    fun `should not confirm a copy when both signals agree`() {
        // GIVEN
        installTimes(recorded = AN_INSTALL_TIME, current = AN_INSTALL_TIME)
        androidIds(recorded = AN_ANDROID_ID, current = AN_ANDROID_ID)

        // WHEN
        val signals = detector.signals()

        // THEN
        assertThat(signals).isEqualTo(Signals(Signal.MATCH, Signal.MATCH))
        assertThat(signals.isConfirmedCopy).isFalse()
    }

    @Test
    fun `should not confirm a copy when nothing was recorded`() {
        // GIVEN a copy made from an install that predates the baselines.
        installTimes(recorded = null, current = A_LATER_INSTALL_TIME)
        androidIds(recorded = null, current = ANOTHER_ANDROID_ID)

        // WHEN
        val signals = detector.signals()

        // THEN
        assertThat(signals).isEqualTo(Signals(Signal.UNKNOWN, Signal.UNKNOWN))
        assertThat(signals.isConfirmedCopy).isFalse()
    }

    @Test
    fun `should not confirm a copy when a current value can't be read`() {
        // GIVEN
        installTimes(recorded = AN_INSTALL_TIME, current = A_LATER_INSTALL_TIME)
        androidIds(recorded = AN_ANDROID_ID, current = null)

        // WHEN
        val signals = detector.signals()

        // THEN
        assertThat(signals).isEqualTo(Signals(Signal.MISMATCH, Signal.UNKNOWN))
        assertThat(signals.isConfirmedCopy).isFalse()
    }

    @Test
    fun `should not confirm a copy when reading a signal throws`() {
        // GIVEN
        every { firstInstallTimeRepository.recordedInstallTimeInMs() } throws RuntimeException()
        every { firstInstallTimeRepository.currentInstallTimeInMs() } returns A_LATER_INSTALL_TIME
        androidIds(recorded = AN_ANDROID_ID, current = ANOTHER_ANDROID_ID)

        // WHEN
        val signals = detector.signals()

        // THEN
        assertThat(signals).isEqualTo(Signals(Signal.UNKNOWN, Signal.MISMATCH))
        assertThat(signals.isConfirmedCopy).isFalse()
    }

    private fun installTimes(recorded: Long?, current: Long?) {
        every { firstInstallTimeRepository.recordedInstallTimeInMs() } returns recorded
        every { firstInstallTimeRepository.currentInstallTimeInMs() } returns current
    }

    private fun androidIds(recorded: String?, current: String?) {
        every { androidIdRepository.recordedAndroidId() } returns recorded
        every { androidIdRepository.currentAndroidId() } returns current
    }
}
