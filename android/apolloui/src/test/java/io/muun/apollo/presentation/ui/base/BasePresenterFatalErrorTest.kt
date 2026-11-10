package io.muun.apollo.presentation.ui.base

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.R
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.os.secure_storage.debugSnapshot
import io.muun.apollo.data.os.secure_storage.secureStorageError
import io.muun.apollo.domain.errors.ChallengeKeyMigrationError
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.selector.LogoutOptionsSelector
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.functions.Action0

class BasePresenterFatalErrorTest : BaseTest() {

    // Which dialog a corrupt store gets is the difference between a wallet that can be restored
    // from its recovery code and one whose only copy of the keys the logout would destroy.

    private val view: BaseView = mockk(relaxed = true)
    private val logoutOptionsSelector: LogoutOptionsSelector = mockk()

    @Test
    fun `should offer the logout to a recoverable wallet`() {
        every { logoutOptionsSelector.isRecoverable() } returns true

        assertThat(presenter().runFatalErrorHandling(secureStorageError())).isTrue()

        verify { view.showErrorDialog(eq(R.string.secure_storage_error), any<Action0>()) }
    }

    @Test
    fun `should offer the error report to an unrecoverable wallet`() {
        every { logoutOptionsSelector.isRecoverable() } returns false

        assertThat(presenter().runFatalErrorHandling(secureStorageError())).isTrue()

        verify {
            view.showErrorDialog(
                eq(R.string.secure_storage_error_avoid_crash_non_logout),
                any<Action0>()
            )
        }
        verify(exactly = 0) {
            view.showErrorDialog(eq(R.string.secure_storage_error), any<Action0>())
        }
    }

    @Test
    fun `should leave an error that is not corruption to the generic handling`() {
        every { logoutOptionsSelector.isRecoverable() } returns true

        assertThat(presenter().runFatalErrorHandling(RuntimeException("boom"))).isFalse()

        verify(exactly = 0) { view.showErrorDialog(any<Int>(), any<Action0>()) }
    }

    @Test
    fun `should leave a failed keystore call to the generic handling`() {
        // Folding OperationFailed into StorageFailed would send a user whose keystore was only
        // momentarily unavailable to restore the wallet from its recovery code.
        every { logoutOptionsSelector.isRecoverable() } returns true

        val error = SecureKeyValueStoreError.OperationFailed(RuntimeException("boom"))

        assertThat(presenter().runFatalErrorHandling(error)).isFalse()

        verify(exactly = 0) { view.showErrorDialog(any<Int>(), any<Action0>()) }
    }

    @Test
    fun `should leave a missing key to the generic handling`() {
        // A key that was never written says nothing about the storage, so the screen that
        // raised it keeps its own answer.
        every { logoutOptionsSelector.isRecoverable() } returns true

        val error = SecureStorageProvider.SecureStorageNoSuchElementError("key", debugSnapshot())

        assertThat(presenter().runFatalErrorHandling(error)).isFalse()

        verify(exactly = 0) { view.showErrorDialog(any<Int>(), any<Action0>()) }
    }

    @Test
    fun `should handle corruption that reaches the presenter wrapped`() {
        // MigrateChallengeKeysAction replaces every throwable with a ChallengeKeyMigrationError,
        // and MigrationPresenter answers anything it is left with by offering a retry. This is
        // the shape that made the plain instanceof check miss it.
        every { logoutOptionsSelector.isRecoverable() } returns true

        val error = ChallengeKeyMigrationError(secureStorageError())

        assertThat(presenter().runFatalErrorHandling(error)).isTrue()

        verify { view.showErrorDialog(eq(R.string.secure_storage_error), any<Action0>()) }
    }

    private fun presenter(): TestPresenter {
        return TestPresenter().also {
            it.wire(logoutOptionsSelector)
            it.setView(view)
        }
    }

    /**
     * handleFatalError is protected, and the collaborators it needs are field-injected. A
     * subclass reaches both without standing up the whole dependency graph.
     */
    private class TestPresenter : BasePresenter<BaseView>() {

        fun wire(selector: LogoutOptionsSelector) {
            logoutOptionsSel = selector
        }

        fun runFatalErrorHandling(error: Throwable): Boolean {
            return handleFatalError(error)
        }
    }
}
