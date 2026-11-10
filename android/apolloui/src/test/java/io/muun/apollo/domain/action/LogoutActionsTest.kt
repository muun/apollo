package io.muun.apollo.domain.action

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import io.mockk.verifyOrder
import io.muun.apollo.BaseTest
import io.muun.apollo.data.async.tasks.TaskScheduler
import io.muun.apollo.data.db.DaoManager
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.data.preferences.AuthRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import io.muun.apollo.domain.ApplicationLockManager
import io.muun.apollo.domain.SignupDraftManager
import io.muun.apollo.domain.action.base.AsyncActionStore
import io.muun.apollo.domain.action.session.ClearRepositoriesAction
import io.muun.apollo.domain.action.session.RebaseInstallBaselinesAction
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.SignupDraft
import io.muun.apollo.domain.selector.LogoutOptionsSelector
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Before
import org.junit.Test

class LogoutActionsTest : BaseTest() {

    @MockK(relaxed = true)
    lateinit var contactActions: ContactActions

    @MockK(relaxed = true)
    lateinit var asyncActionStore: AsyncActionStore

    @MockK(relaxed = true)
    lateinit var daoManager: DaoManager

    @MockK(relaxed = true)
    lateinit var lockManager: ApplicationLockManager

    @MockK(relaxed = true)
    lateinit var logoutOptionsSelector: LogoutOptionsSelector

    @MockK(relaxed = true)
    lateinit var signupDraftManager: SignupDraftManager

    @MockK(relaxed = true)
    lateinit var clearRepositories: ClearRepositoriesAction

    @MockK(relaxed = true)
    lateinit var taskScheduler: TaskScheduler

    @MockK(relaxed = true)
    internal lateinit var secureKeyValueStoreRepository: SecureKeyValueStoreRepository

    @MockK(relaxed = true)
    lateinit var notificationService: NotificationService

    @MockK(relaxed = true)
    lateinit var libwalletClient: LibwalletClient

    @MockK(relaxed = true)
    lateinit var userRepository: UserRepository

    @MockK(relaxed = true)
    lateinit var authRepository: AuthRepository

    @MockK(relaxed = true)
    lateinit var rebaseInstallBaselines: RebaseInstallBaselinesAction

    private lateinit var logoutActions: LogoutActions

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        logoutActions = LogoutActions(
            contactActions,
            asyncActionStore,
            daoManager,
            lockManager,
            logoutOptionsSelector,
            signupDraftManager,
            clearRepositories,
            taskScheduler,
            secureKeyValueStoreRepository,
            notificationService,
            libwalletClient,
            userRepository,
            authRepository,
            rebaseInstallBaselines,
        )
    }

    @Test
    fun `should keep the signup draft across the wipe when starting clean`() {
        // Given
        val draft = SignupDraft()
        every { signupDraftManager.fetchSignupDraft() } returns draft

        // When
        logoutActions.destroyWalletToStartClean()

        // Then
        verifyOrder {
            secureKeyValueStoreRepository.wipe()
            signupDraftManager.save(draft)
        }
    }

    @Test
    fun `should still wipe when starting clean and the signup draft can't be read`() {
        // Given
        every { signupDraftManager.fetchSignupDraft() } throws
            SecureKeyValueStoreError.StorageFailed(IllegalStateException("inconsistent mode"))

        // When
        logoutActions.destroyWalletToStartClean()

        // Then
        verify { secureKeyValueStoreRepository.wipe() }
        verify(exactly = 0) { signupDraftManager.save(any()) }
    }

    @Test
    fun `destroyRecoverableWallet should wipe the wallet when reading the stored user fails`() {
        // GIVEN
        every { logoutOptionsSelector.isRecoverable() } returns true
        every { userRepository.fetchHid() } throws IllegalStateException("corrupt stored user")

        // WHEN
        logoutActions.destroyRecoverableWallet()

        // THEN
        verify { libwalletClient.resetData() }
    }

    @Test
    fun `should rebase the install baselines after wiping a recoverable wallet`() {
        // GIVEN
        every { logoutOptionsSelector.isRecoverable() } returns true

        // WHEN
        logoutActions.destroyRecoverableWallet()

        // THEN
        verifyOrder {
            secureKeyValueStoreRepository.wipe()
            rebaseInstallBaselines.run()
        }
    }

    @Test
    fun `should rebase the install baselines after wiping to start clean`() {
        // WHEN
        logoutActions.destroyWalletToStartClean()

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
        val thrown = catchThrowable { logoutActions.destroyWalletToStartClean() }

        // THEN
        assertThat(thrown).hasMessage("boom")
        verify(exactly = 1) { rebaseInstallBaselines.run() }
    }

    @Test
    fun `should neither wipe nor rebase when the wallet is not recoverable`() {
        // GIVEN
        every { logoutOptionsSelector.isRecoverable() } returns false

        // WHEN
        logoutActions.destroyRecoverableWallet()

        // THEN
        verify(exactly = 0) { secureKeyValueStoreRepository.wipe() }
        verify(exactly = 0) { rebaseInstallBaselines.run() }
    }
}
