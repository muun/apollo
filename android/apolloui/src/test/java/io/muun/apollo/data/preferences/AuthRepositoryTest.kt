package io.muun.apollo.data.preferences

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.spyk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreTestWiring
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest : BaseTest() {

    companion object {
        private const val SERVER_JWT = "header.payload.signature"

        // Mirrors the private constant in AuthRepository for test setup
        private const val KEY_SERVER_JWT = "server_jwt"
    }

    @MockK(relaxed = true)
    lateinit var context: Context

    @MockK(relaxed = true)
    lateinit var repositoryRegistry: RepositoryRegistry

    private lateinit var keyStore: FakeKeyStore

    private lateinit var secureStorageProvider: SecureStorageProvider

    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        keyStore = FakeKeyStore()
        secureStorageProvider = SecureStorageProvider(keyStore, FakePreferences())
        authRepository = authRepositoryWith(
            SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider)
        )
    }

    private fun authRepositoryWith(repository: SecureKeyValueStoreRepository): AuthRepository {
        return AuthRepository(context, repositoryRegistry, repository)
    }

    @Test
    fun `should return empty when no server jwt is stored`() {
        // Given (nothing stored)

        // When
        val actual = authRepository.serverJwt

        // Then
        assertFalse(actual.isPresent)
    }

    @Test
    fun `should return the stored server jwt`() {
        // Given
        authRepository.storeServerJwt(SERVER_JWT)

        // When
        val actual = authRepository.serverJwt

        // Then
        assertEquals(SERVER_JWT, actual.get())
    }

    @Test
    fun `should fail with DecryptionFailed when the stored server jwt can't be decrypted`() {
        // Given (the ciphertext is there but its keystore entry is gone)
        authRepository.storeServerJwt(SERVER_JWT)
        keyStore.deleteEntry(KEY_SERVER_JWT)

        // When / Then
        assertThatThrownBy { authRepository.serverJwt }
            .isInstanceOf(SecureKeyValueStoreError.DecryptionFailed::class.java)
    }

    @Test
    fun `should return empty after clearing the session`() {
        // Given
        authRepository.storeServerJwt(SERVER_JWT)

        // When
        authRepository.clearSession()

        // Then
        assertFalse(authRepository.serverJwt.isPresent)
    }

    @Test
    fun `should wipe the server jwt bytes after storing them`() {
        // Given
        val repository = spyk(SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider))
        val handed = slot<ByteArray>()
        var handedContent = ""
        every { repository.put(KEY_SERVER_JWT, capture(handed)) } answers {
            handedContent = String(handed.captured)
            callOriginal()
        }

        // When
        authRepositoryWith(repository).storeServerJwt(SERVER_JWT)

        // Then
        assertEquals(SERVER_JWT, handedContent)
        assertTrue(handed.captured.all { it == 0.toByte() })
    }

    @Test
    fun `should wipe the server jwt bytes when storing them fails`() {
        // Given
        val repository = spyk(SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider))
        val handed = slot<ByteArray>()
        var handedContent = ""
        every { repository.put(KEY_SERVER_JWT, capture(handed)) } answers {
            handedContent = String(handed.captured)
            throw IllegalStateException("storage unavailable")
        }

        // When / Then
        assertThatThrownBy { authRepositoryWith(repository).storeServerJwt(SERVER_JWT) }
            .isInstanceOf(IllegalStateException::class.java)
        assertEquals(SERVER_JWT, handedContent)
        assertTrue(handed.captured.all { it == 0.toByte() })
    }
}
