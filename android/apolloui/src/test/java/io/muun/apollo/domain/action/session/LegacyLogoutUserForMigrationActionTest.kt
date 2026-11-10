package io.muun.apollo.domain.action.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.muun.apollo.BaseTest
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test

class LegacyLogoutUserForMigrationActionTest : BaseTest() {

    private val secureKeyValueStoreRepository: SecureKeyValueStoreRepository = mockk(relaxed = true)
    private val rebaseInstallBaselines: RebaseInstallBaselinesAction = mockk(relaxed = true)

    private val action = LegacyLogoutUserForMigrationAction(
        mockk(relaxed = true),
        mockk(relaxed = true),
        secureKeyValueStoreRepository,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        rebaseInstallBaselines,
    )

    @Test
    fun `should rebase the install baselines after wiping secure storage`() {
        // WHEN
        action.run()

        // THEN
        verifyOrder {
            secureKeyValueStoreRepository.wipe()
            rebaseInstallBaselines.run()
        }
    }

    @Test
    fun `should rebase the install baselines even when the wipe fails`() {
        // GIVEN a wipe that may have removed part of the wallet before failing.
        every { secureKeyValueStoreRepository.wipe() } throws RuntimeException("boom")

        // WHEN
        val thrown = catchThrowable { action.run() }

        // THEN
        assertThat(thrown).hasMessage("boom")
        verify(exactly = 1) { rebaseInstallBaselines.run() }
    }
}
