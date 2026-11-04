package io.muun.apollo.domain.action.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.data.preferences.AndroidIdRepository
import io.muun.apollo.data.preferences.FirstInstallTimeRepository
import org.junit.Test

class RebaseInstallBaselinesActionTest : BaseTest() {

    private val firstInstallTimeRepository: FirstInstallTimeRepository = mockk(relaxed = true)
    private val androidIdRepository: AndroidIdRepository = mockk(relaxed = true)

    private val action =
        RebaseInstallBaselinesAction(firstInstallTimeRepository, androidIdRepository)

    @Test
    fun `should rebase both baselines`() {
        // WHEN
        action.run()

        // THEN
        verify(exactly = 1) { firstInstallTimeRepository.rebaseToCurrentInstall() }
        verify(exactly = 1) { androidIdRepository.rebaseToCurrentInstall() }
    }

    @Test
    fun `should rebase the second baseline and not throw when the first one fails`() {
        // GIVEN
        every { firstInstallTimeRepository.rebaseToCurrentInstall() } throws RuntimeException()

        // WHEN
        action.run()

        // THEN
        verify(exactly = 1) { androidIdRepository.rebaseToCurrentInstall() }
    }
}
