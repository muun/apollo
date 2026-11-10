package io.muun.apollo.domain.action.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.muun.apollo.BaseTest
import io.muun.apollo.data.preferences.AndroidIdRepository
import io.muun.apollo.data.preferences.FirstInstallTimeRepository
import io.muun.apollo.domain.copieddevice.DeviceCopyDetector
import io.muun.apollo.domain.copieddevice.DeviceCopyDetector.Signal
import org.junit.Test

class RecordInstallBaselinesActionTest : BaseTest() {

    private val firstInstallTimeRepository: FirstInstallTimeRepository = mockk(relaxed = true)
    private val androidIdRepository: AndroidIdRepository = mockk(relaxed = true)
    private val deviceCopyDetector: DeviceCopyDetector = mockk(relaxed = true)

    private val action = RecordInstallBaselinesAction(
        firstInstallTimeRepository,
        androidIdRepository,
        deviceCopyDetector,
    )

    @Test
    fun `should record both baselines when the first install time matches`() {
        // GIVEN
        every { deviceCopyDetector.firstInstallTimeSignal() } returns Signal.MATCH

        // WHEN
        action.run()

        // THEN
        verifyOrder {
            firstInstallTimeRepository.recordCurrentInstallTimeIfAbsent()
            deviceCopyDetector.firstInstallTimeSignal()
            androidIdRepository.recordCurrentAndroidIdIfAbsent()
        }
    }

    @Test
    fun `should not record the ANDROID_ID when the first install time disagrees`() {
        // GIVEN
        every { deviceCopyDetector.firstInstallTimeSignal() } returns Signal.MISMATCH

        // WHEN
        action.run()

        // THEN
        verify(exactly = 0) { androidIdRepository.recordCurrentAndroidIdIfAbsent() }
    }

    @Test
    fun `should not record the ANDROID_ID when the first install time is unknown`() {
        // GIVEN
        every { deviceCopyDetector.firstInstallTimeSignal() } returns Signal.UNKNOWN

        // WHEN
        action.run()

        // THEN
        verify(exactly = 0) { androidIdRepository.recordCurrentAndroidIdIfAbsent() }
    }

    @Test
    fun `should record the ANDROID_ID and not throw when the first install time fails`() {
        // GIVEN
        every {
            firstInstallTimeRepository.recordCurrentInstallTimeIfAbsent()
        } throws RuntimeException()
        every { deviceCopyDetector.firstInstallTimeSignal() } returns Signal.MATCH

        // WHEN
        action.run()

        // THEN
        verify(exactly = 1) { androidIdRepository.recordCurrentAndroidIdIfAbsent() }
    }

    @Test
    fun `should not throw when recording the ANDROID_ID fails`() {
        // GIVEN
        every { deviceCopyDetector.firstInstallTimeSignal() } returns Signal.MATCH
        every { androidIdRepository.recordCurrentAndroidIdIfAbsent() } throws RuntimeException()

        // WHEN
        action.run()

        // THEN
        verify(exactly = 1) { firstInstallTimeRepository.recordCurrentInstallTimeIfAbsent() }
    }
}
