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
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.Schema
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.TestNet3Params
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeysRepositoryTest : BaseTest() {

    companion object {
        // Mirror the private constants in KeysRepository for test setup
        private const val KEY_BASE_58_PRIVATE_KEY = "key_base_58_private_key"
        private const val KEY_CHALLENGE_PUBLIC_KEY = "key_challenge_public_key_"

        private const val BASE_PRIVATE_KEY_VALUE = "dummy-base-key"

        // Matches the version used by ChallengeKey.buildPublic for RC V2
        private const val CHALLENGE_KEY_VERSION_RC_V2 = 2
    }

    private val keyStore = FakeKeyStore()

    private lateinit var secureStorageProvider: SecureStorageProvider

    // The derivation path lives in plain preferences, so the round trip needs real storage.
    private val sharedPreferences = FakeSharedPreferences()

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
            keyStore,
            FakePreferences(),
        )

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences

        keysRepository = KeysRepository(
            context,
            repositoryRegistry,
            networkParameters,
            SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider),
            userRepository,
            secureStorageProvider,
        )
    }

    private fun keysRepositoryWith(repository: SecureKeyValueStoreRepository): KeysRepository {
        return KeysRepository(
            context,
            repositoryRegistry,
            networkParameters,
            repository,
            userRepository,
            secureStorageProvider,
        )
    }

    private fun createTestChallengeKey(version: Int = CHALLENGE_KEY_VERSION_RC_V2): ChallengePublicKey {
        val ecKey = ECKey()
        val salt = ByteArray(8) { it.toByte() }
        return ChallengePublicKey(ecKey.pubKey, salt, version)
    }

    private fun fetchChallengePublicKey(type: ChallengeType): ChallengePublicKey {
        return keysRepository.getChallengePublicKey(type).toBlocking().first()
    }

    private fun storeLegacyChallengeKey(type: ChallengeType, serialization: ByteArray) {
        secureStorageProvider.put(KEY_CHALLENGE_PUBLIC_KEY + type, serialization)
    }

    // -- Base private key --

    @Test
    fun `should store the base private key derived at the base path`() {
        // Given
        val rootPrivateKey = PrivateKey.getNewRootPrivateKey(TestNet3Params.get())
        val expected = rootPrivateKey.deriveFromAbsolutePath(Schema.getBasePath()).serializeBase58()

        // When
        keysRepository.storeBasePrivateKey(rootPrivateKey).toBlocking().subscribe()

        // Then
        assertTrue(keysRepository.hasBasePrivateKey)
        assertEquals(expected, String(secureStorageProvider.get(KEY_BASE_58_PRIVATE_KEY)))
    }

    @Test
    fun `should wipe the serialized base private key after storing it`() {
        // Given
        val rootPrivateKey = PrivateKey.getNewRootPrivateKey(TestNet3Params.get())
        val expected = rootPrivateKey.deriveFromAbsolutePath(Schema.getBasePath()).serializeBase58()
        val repository = spyk(SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider))
        val handed = slot<ByteArray>()
        var handedContent = ""
        every { repository.put(KEY_BASE_58_PRIVATE_KEY, capture(handed)) } answers {
            handedContent = String(handed.captured)
            callOriginal()
        }

        // When
        keysRepositoryWith(repository).storeBasePrivateKey(rootPrivateKey).toBlocking().subscribe()

        // Then
        assertEquals(expected, handedContent)
        assertTrue(handed.captured.all { it == 0.toByte() })
    }

    @Test
    fun `should wipe the serialized base private key when storing it fails`() {
        // Given
        val rootPrivateKey = PrivateKey.getNewRootPrivateKey(TestNet3Params.get())
        val expected = rootPrivateKey.deriveFromAbsolutePath(Schema.getBasePath()).serializeBase58()
        val repository = spyk(SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider))
        val handed = slot<ByteArray>()
        var handedContent = ""
        every { repository.put(KEY_BASE_58_PRIVATE_KEY, capture(handed)) } answers {
            handedContent = String(handed.captured)
            throw IllegalStateException("storage unavailable")
        }

        // When / Then
        assertThatThrownBy {
            keysRepositoryWith(repository).storeBasePrivateKey(rootPrivateKey)
                .toBlocking()
                .subscribe()
        }.isInstanceOf(IllegalStateException::class.java)
        assertEquals(expected, handedContent)
        assertTrue(handed.captured.all { it == 0.toByte() })
    }

    @Test
    fun `should read back the stored base private key`() {
        // Given
        val rootPrivateKey = PrivateKey.getNewRootPrivateKey(TestNet3Params.get())
        val expected = rootPrivateKey.deriveFromAbsolutePath(Schema.getBasePath())
        keysRepository.storeBasePrivateKey(rootPrivateKey).toBlocking().subscribe()

        // When
        val actual = keysRepository.basePrivateKey.toBlocking().first()

        // Then
        assertEquals(expected.serializeBase58(), actual.serializeBase58())
        assertEquals(expected.absoluteDerivationPath, actual.absoluteDerivationPath)
    }

    @Test
    fun `should fail with NotFound when no base private key is stored`() {
        // Given (nothing stored)

        // When / Then
        assertThatThrownBy { keysRepository.basePrivateKey.toBlocking().first() }
            .isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
    }

    // -- Base private key keystore probe --

    @Test
    fun `should report the base private key's keystore key as missing when it's gone`() {
        // Given the shape a device-to-device copy leaves behind
        secureStorageProvider.put(KEY_BASE_58_PRIVATE_KEY, BASE_PRIVATE_KEY_VALUE.toByteArray())
        keyStore.wipe()

        // When
        val isMissing = keysRepository.isBasePrivateKeyKeystoreKeyMissing

        // Then
        assertTrue(isMissing)
    }

    @Test
    fun `should not report the keystore key as missing when it's present`() {
        // Given
        secureStorageProvider.put(KEY_BASE_58_PRIVATE_KEY, BASE_PRIVATE_KEY_VALUE.toByteArray())

        // When
        val isMissing = keysRepository.isBasePrivateKeyKeystoreKeyMissing

        // Then
        assertFalse(isMissing)
    }

    @Test
    fun `should not report the keystore key as missing when no key is stored`() {
        // Given nothing stored

        // When
        val isMissing = keysRepository.isBasePrivateKeyKeystoreKeyMissing

        // Then
        assertFalse(isMissing)
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
    fun `should fail with NotFound when no encrypted base private key is stored`() {
        // Given (nothing stored)

        // When / Then
        assertThatThrownBy { keysRepository.encryptedBasePrivateKey.toBlocking().first() }
            .isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
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
    fun `should fail with NotFound when no encrypted muun private key is stored`() {
        // Given (nothing stored)

        // When / Then
        assertThatThrownBy { keysRepository.encryptedMuunPrivateKey.toBlocking().first() }
            .isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
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
        val actual = fetchChallengePublicKey(type)
        assertEquals(expected.version, actual.version)
        assertTrue(expected.salt.contentEquals(actual.salt))
        // No public getter for the raw key bytes, so compare via full serialization
        assertTrue(expected.serialize().contentEquals(actual.serialize()))
    }

    @Test
    fun `should fail with NotFound when no challenge public key is stored`() {
        // Given (nothing stored)

        // When / Then
        assertThatThrownBy {
            fetchChallengePublicKey(ChallengeType.PASSWORD)
        }.isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
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

    // -- Challenge keys migration --

    @Test
    fun `should report challenge keys as migrated when none is stored`() {
        // Given (nothing stored)

        // When
        val actual = keysRepository.hasMigratedChallengeKeys()

        // Then (nothing to migrate for a user without challenge keys)
        assertTrue(actual)
    }

    @Test
    fun `should report challenge keys as migrated after storing a versioned key`() {
        // Given
        keysRepository.storePublicChallengeKey(
            createTestChallengeKey(),
            ChallengeType.PASSWORD,
        )

        // Then
        assertTrue(keysRepository.hasMigratedChallengeKeys())
    }

    @Test
    fun `should report challenge keys as not migrated for a legacy key without salt`() {
        // Given (old app versions stored the bare public key)
        storeLegacyChallengeKey(ChallengeType.PASSWORD, ECKey().pubKey)

        // Then
        assertFalse(keysRepository.hasMigratedChallengeKeys())
    }

    @Test
    fun `should add the salt to a legacy challenge key`() {
        // Given (old app versions stored the bare public key)
        val legacyPublicKey = ECKey().pubKey
        val salt = ByteArray(8) { it.toByte() }
        storeLegacyChallengeKey(ChallengeType.PASSWORD, legacyPublicKey)

        // When
        keysRepository.seasonPublicChallengeKey(salt, ChallengeType.PASSWORD)

        // Then
        val expected = ChallengePublicKey.buildLegacy(legacyPublicKey, salt)
        val actual = fetchChallengePublicKey(ChallengeType.PASSWORD)
        assertTrue(expected.serialize().contentEquals(actual.serialize()))
    }

    @Test
    fun `should leave a salted challenge key untouched when adding the salt again`() {
        // Given
        val stored = createTestChallengeKey()
        keysRepository.storePublicChallengeKey(stored, ChallengeType.PASSWORD)

        // When
        keysRepository.seasonPublicChallengeKey(ByteArray(8), ChallengeType.PASSWORD)

        // Then
        val actual = fetchChallengePublicKey(ChallengeType.PASSWORD)
        assertTrue(stored.serialize().contentEquals(actual.serialize()))
    }

    @Test
    fun `should add the version to a salted legacy challenge key`() {
        // Given (the salt migration ran on an app version that had no key versions yet)
        val legacyPublicKey = ECKey().pubKey
        val salt = ByteArray(8) { it.toByte() }
        storeLegacyChallengeKey(ChallengeType.PASSWORD, legacyPublicKey + salt)

        // When
        keysRepository.addChallengePublicKeyVersionMigration(ChallengeType.PASSWORD)

        // Then
        val expected = ChallengePublicKey.buildLegacy(legacyPublicKey, salt)
        val actual = fetchChallengePublicKey(ChallengeType.PASSWORD)
        assertTrue(expected.serialize().contentEquals(actual.serialize()))
    }

    @Test
    fun `should skip the version migration for a challenge key without salt`() {
        // Given
        val legacyPublicKey = ECKey().pubKey
        storeLegacyChallengeKey(ChallengeType.PASSWORD, legacyPublicKey)

        // When
        keysRepository.addChallengePublicKeyVersionMigration(ChallengeType.PASSWORD)

        // Then (still the bare public key: the salt migration is the one that rewrites it)
        val stored = secureStorageProvider.get(KEY_CHALLENGE_PUBLIC_KEY + ChallengeType.PASSWORD)
        assertTrue(legacyPublicKey.contentEquals(stored))
    }

    @Test
    fun `should fail with NotFound when adding the salt to a missing challenge key`() {
        // Given (nothing stored)

        // When / Then
        assertThatThrownBy {
            keysRepository.seasonPublicChallengeKey(ByteArray(8), ChallengeType.PASSWORD)
        }.isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
    }

    @Test
    fun `should fail with NotFound when adding the version to a missing challenge key`() {
        // Given (nothing stored)

        // When / Then
        assertThatThrownBy {
            keysRepository.addChallengePublicKeyVersionMigration(ChallengeType.PASSWORD)
        }.isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
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
