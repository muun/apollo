package io.muun.apollo.domain.action.ek

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.crypto.hd.PrivateKey
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import rx.Observable

class GetOrCreateEncryptedBasePrivateKeyActionTest : BaseTest() {

    companion object {
        private const val STORED_KEY = "stored-encrypted-base-private-key"
        private const val CREATED_KEY = "created-encrypted-base-private-key"
    }

    @MockK(relaxed = true)
    lateinit var keysRepository: KeysRepository

    private lateinit var action: GetOrCreateEncryptedBasePrivateKeyAction

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        action = GetOrCreateEncryptedBasePrivateKeyAction(keysRepository)
    }

    @Test
    fun `should return the stored encrypted base private key`() {
        // Given
        every { keysRepository.encryptedBasePrivateKey } returns Observable.just(STORED_KEY)

        // When
        val actual = action.action().toBlocking().first()

        // Then
        assertEquals(STORED_KEY, actual)
        verify(exactly = 0) { keysRepository.storeEncryptedBasePrivateKey(any()) }
    }

    @Test
    fun `should create and store the encrypted base private key when none is stored`() {
        // Given
        every { keysRepository.encryptedBasePrivateKey } returns
            Observable.error(SecureKeyValueStoreError.NotFound())
        givenTheKeysToCreateItFrom()

        // When
        val actual = action.action().toBlocking().first()

        // Then
        assertEquals(CREATED_KEY, actual)
        verify { keysRepository.storeEncryptedBasePrivateKey(CREATED_KEY) }
    }

    @Test
    fun `should not create the encrypted base private key when reading it fails otherwise`() {
        // Given
        val error = SecureKeyValueStoreError.DecryptionFailed(IllegalStateException("bad key"))
        every { keysRepository.encryptedBasePrivateKey } returns Observable.error(error)
        givenTheKeysToCreateItFrom()

        // When / Then
        assertThatThrownBy { action.action().toBlocking().first() }.isSameAs(error)
        verify(exactly = 0) { keysRepository.storeEncryptedBasePrivateKey(any()) }
    }

    private fun givenTheKeysToCreateItFrom() {
        val challengePublicKey = mockk<ChallengePublicKey>()
        every {
            challengePublicKey.encryptPrivateKey(any<String>(), any<PrivateKey>(), any<Long>())
        } returns CREATED_KEY

        every { keysRepository.hasChallengePublicKey(ChallengeType.RECOVERY_CODE) } returns true
        every { keysRepository.hasEncryptedMuunPrivateKey } returns true
        every { keysRepository.hasBasePrivateKey } returns true
        every { keysRepository.encryptedMuunPrivateKey } returns Observable.just("muun-key")
        val basePrivateKey = mockk<PrivateKey>()
        every { basePrivateKey.fingerprint } returns ByteArray(4)
        every { keysRepository.basePrivateKey } returns Observable.just(basePrivateKey)
        every { keysRepository.getChallengePublicKey(ChallengeType.RECOVERY_CODE) } returns
            Observable.just(challengePublicKey)
    }
}
