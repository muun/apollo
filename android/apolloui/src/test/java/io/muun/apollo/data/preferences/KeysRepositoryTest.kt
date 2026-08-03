package io.muun.apollo.data.preferences

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeysRepositoryTest : BaseTest() {

    companion object {
        // Mirrors the private constant in KeysRepository for test setup
        private const val KEY_BASE_58_PRIVATE_KEY = "key_base_58_private_key"

        // Matches the version used by ChallengeKey.buildPublic for RC V2
        private const val CHALLENGE_KEY_VERSION_RC_V2 = 2
    }

    private lateinit var secureStorageProvider: SecureStorageProvider

    @MockK(relaxed = true)
    lateinit var context: Context

    @MockK(relaxed = true)
    lateinit var repositoryRegistry: RepositoryRegistry

    @MockK(relaxed = true)
    lateinit var networkParameters: NetworkParameters

    @MockK(relaxed = true)
    lateinit var userRepository: UserRepository

    private lateinit var keysRepository: KeysRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        secureStorageProvider = SecureStorageProvider(
            FakeKeyStore(),
            FakePreferences(),
        )

        keysRepository = KeysRepository(
            context,
            repositoryRegistry,
            networkParameters,
            secureStorageProvider,
            userRepository,
        )
    }

    private fun createTestChallengeKey(version: Int = CHALLENGE_KEY_VERSION_RC_V2): ChallengePublicKey {
        val ecKey = ECKey()
        val salt = ByteArray(8) { it.toByte() }
        return ChallengePublicKey(ecKey.pubKey, salt, version)
    }

    // -- Encrypted user key (base private key) --

    @Test
    fun `should store and retrieve encrypted base private key`() {
        // Given
        val expected = "encrypted-base-private-key-data"

        // When
        keysRepository.storeEncryptedBasePrivateKey(expected)

        // Then
        val actual = keysRepository.encryptedBasePrivateKey.toBlocking().first()
        assertEquals(expected, actual)
    }

    @Test
    fun `should report encrypted base private key as present after storing`() {
        // When
        keysRepository.storeEncryptedBasePrivateKey("some-data")

        // Then
        assertTrue(keysRepository.hasEncryptedBasePrivateKey)
    }

    @Test
    fun `should clear encrypted base private key on wipe`() {
        // Given
        keysRepository.storeEncryptedBasePrivateKey("some-data")

        // When
        keysRepository.wipeEncryptedBasePrivateKey()

        // Then
        assertFalse(keysRepository.hasEncryptedBasePrivateKey)
    }

    // -- Encrypted muun key --

    @Test
    fun `should store and retrieve encrypted muun private key`() {
        // Given
        val expected = "encrypted-muun-private-key-data"

        // When
        keysRepository.storeEncryptedMuunPrivateKey(expected)

        // Then
        val actual = keysRepository.encryptedMuunPrivateKey.toBlocking().first()
        assertEquals(expected, actual)
    }

    @Test
    fun `should report encrypted muun private key as present after storing`() {
        // When
        keysRepository.storeEncryptedMuunPrivateKey("some-data")

        // Then
        assertTrue(keysRepository.hasEncryptedMuunPrivateKey)
    }

    // -- Challenge keys --

    @Test
    fun `should store and retrieve PASSWORD challenge public key`() {
        verifyChallengeKeyRoundTrip(ChallengeType.PASSWORD)
    }

    @Test
    fun `should store and retrieve RECOVERY_CODE challenge public key`() {
        verifyChallengeKeyRoundTrip(ChallengeType.RECOVERY_CODE)
    }

    @Test
    fun `should store and retrieve USER_KEY challenge public key`() {
        verifyChallengeKeyRoundTrip(ChallengeType.USER_KEY)
    }

    private fun verifyChallengeKeyRoundTrip(type: ChallengeType) {
        // Given
        val expected = createTestChallengeKey()

        // When
        keysRepository.storePublicChallengeKey(expected, type)

        // Then
        val actual = keysRepository.getChallengePublicKey(type).toBlocking().first()
        assertEquals(expected.version, actual.version)
        assertTrue(expected.salt.contentEquals(actual.salt))
        // No public getter for the raw key bytes, so compare via full serialization
        assertTrue(expected.serialize().contentEquals(actual.serialize()))
    }

    @Test
    fun `should report challenge public key as present after storing`() {
        // When
        keysRepository.storePublicChallengeKey(
            createTestChallengeKey(),
            ChallengeType.PASSWORD,
        )

        // Then
        assertTrue(keysRepository.hasChallengePublicKey(ChallengeType.PASSWORD))
    }

    @Test
    fun `should keep challenge keys independent across types`() {
        // When
        keysRepository.storePublicChallengeKey(
            createTestChallengeKey(),
            ChallengeType.PASSWORD,
        )

        // Then
        assertTrue(keysRepository.hasChallengePublicKey(ChallengeType.PASSWORD))
        assertFalse(keysRepository.hasChallengePublicKey(ChallengeType.RECOVERY_CODE))
        assertFalse(keysRepository.hasChallengePublicKey(ChallengeType.USER_KEY))
    }

    // -- RC rotation side effect --

    @Test
    fun `should wipe encrypted base key when storing RECOVERY_CODE challenge with existing base key`() {
        // Given
        secureStorageProvider.put(KEY_BASE_58_PRIVATE_KEY, "dummy-base-key".toByteArray())
        keysRepository.storeEncryptedBasePrivateKey("encrypted-data")

        // When
        keysRepository.storePublicChallengeKey(
            createTestChallengeKey(),
            ChallengeType.RECOVERY_CODE,
        )

        // Then
        assertFalse(keysRepository.hasEncryptedBasePrivateKey)
    }

    @Test
    fun `should not wipe when storing RECOVERY_CODE challenge without base key`() {
        // Regression guard for the SecureKeyStorage bridge migration: the `if (hasBasePrivateKey)`
        // check in storePublicChallengeKey() exists to skip an unnecessary wipe when the base key
        // is absent. This scenario shouldn't happen in production (a logged-in user always has a
        // base key), but the guard will be touched when RC rotation moves to Go — this test pins
        // its current behavior so a refactor can't silently change it.

        // Given (no base private key in storage)
        keysRepository.storeEncryptedBasePrivateKey("encrypted-data")

        // When
        keysRepository.storePublicChallengeKey(
            createTestChallengeKey(),
            ChallengeType.RECOVERY_CODE,
        )

        // Then
        assertTrue(keysRepository.hasEncryptedBasePrivateKey)
    }

    @Test
    fun `should not wipe encrypted base key when storing PASSWORD challenge`() {
        // Given
        secureStorageProvider.put(KEY_BASE_58_PRIVATE_KEY, "dummy-base-key".toByteArray())
        keysRepository.storeEncryptedBasePrivateKey("encrypted-data")

        // When
        keysRepository.storePublicChallengeKey(
            createTestChallengeKey(),
            ChallengeType.PASSWORD,
        )

        // Then
        assertTrue(keysRepository.hasEncryptedBasePrivateKey)
    }

    // -- Has checks on empty storage --

    @Test
    fun `should report all keys absent when storage is empty`() {
        // Then
        assertFalse(keysRepository.hasBasePrivateKey)
        assertFalse(keysRepository.hasEncryptedBasePrivateKey)
        assertFalse(keysRepository.hasEncryptedMuunPrivateKey)
        assertFalse(keysRepository.hasChallengePublicKey(ChallengeType.PASSWORD))
        assertFalse(keysRepository.hasChallengePublicKey(ChallengeType.RECOVERY_CODE))
        assertFalse(keysRepository.hasChallengePublicKey(ChallengeType.USER_KEY))
    }
}
