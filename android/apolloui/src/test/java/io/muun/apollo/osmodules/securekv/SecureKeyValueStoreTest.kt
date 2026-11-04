package io.muun.apollo.osmodules.securekv

import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.MuunKeyStoreException
import io.muun.apollo.data.os.secure_storage.SecureStorageMode
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.os.secure_storage.debugSnapshot
import io.muun.apollo.data.os.secure_storage.secureStorageError
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Before
import org.junit.Test

class SecureKeyValueStoreTest : BaseTest() {

    private lateinit var keystore: FakeKeyStore
    private lateinit var preferences: FakePreferences
    private lateinit var secureStorageProvider: SecureStorageProvider
    private lateinit var store: SecureKeyValueStore

    @Before
    fun setUp() {
        keystore = FakeKeyStore()
        preferences = FakePreferences()
        secureStorageProvider = SecureStorageProvider(keystore, preferences)
        store = SecureKeyValueStore(secureStorageProvider)
    }

    @Test
    fun `get throws NotFound when key is missing`() {
        assertThatThrownBy { store.get("missing").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
    }

    @Test
    fun `get throws KeystoreKeyMissing when keystore entry is gone`() {
        // Given: key was stored, then the keystore entry is removed (simulates a
        // lock-screen change wiping the encryption key while ciphertext stays in prefs).
        secureStorageProvider.put("key", "value".toByteArray())
        keystore.deleteEntry("key")

        assertThatThrownBy { store.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.KeystoreKeyMissing::class.java)
    }

    @Test
    fun `the wrapper carries the metadata of the error that produced it`() {
        // Callers work around KeystoreKeyMissing by rewriting the value, and they decide whether
        // to do so by reading the legacy diagnostics. Those must survive the wrapping.
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnGet(
                SecureStorageProvider.KeyStoreCorruptedError(
                    "key",
                    debugSnapshot(labelsWithIvInPrefs = setOf("key"))
                )
            )
        )

        val error = catchThrowable { brokenStore.get("key").withSecret { } }

        assertThat(error).isInstanceOf(SecureKeyValueStoreError.KeystoreKeyMissing::class.java)
        assertThat((error as SecureKeyValueStoreError).metadata["labelsWithIvInPrefs"])
            .isEqualTo("key")
    }

    @Test
    fun `get throws StorageFailed on an inconsistent storage mode`() {
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnGet(SecureStorageProvider.InconsistentModeError(debugSnapshot()))
        )

        assertThatThrownBy { brokenStore.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.StorageFailed::class.java)
    }

    @Test
    fun `get throws StorageFailed when the ciphertext is gone`() {
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnGet(
                SecureStorageProvider.SharedPreferencesCorruptedError("key", debugSnapshot())
            )
        )

        assertThatThrownBy { brokenStore.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.StorageFailed::class.java)
    }

    @Test
    fun `get throws DecryptionFailed when decryption itself fails`() {
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnGet(secureStorageError())
        )

        // Not KeystoreKeyMissing: the key material is there, it just didn't open the value.
        // Callers tell the two apart to pick a recovery, so the split has to hold.
        assertThatThrownBy { brokenStore.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.DecryptionFailed::class.java)
            .isNotInstanceOf(SecureKeyValueStoreError.KeystoreKeyMissing::class.java)
    }

    @Test
    fun `get throws OperationFailed when the keystore probe itself fails`() {
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnGet(MuunKeyStoreException(RuntimeException("boom")))
        )

        assertThatThrownBy { brokenStore.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.OperationFailed::class.java)
    }

    @Test
    fun `get throws StorageFailed when the mode check itself fails`() {
        // The mode check reads the MODE preference raw and runs before either path does
        // anything else, so a corrupt one has to classify the same way on both.
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnGet(IllegalArgumentException("No enum constant"))
        )

        assertThatThrownBy { brokenStore.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.StorageFailed::class.java)
    }

    @Test
    fun `get exposes the plaintext of a stored key`() {
        secureStorageProvider.put("key", "plaintext".toByteArray())

        assertThat(readString("key")).isEqualTo("plaintext")
    }

    @Test
    fun `get survives the Secret wiping what it handed out`() {
        // Secret zeroes the array it received, so a store handing out its own backing array
        // would serve zeros from the second read on.
        secureStorageProvider.put("key", "plaintext".toByteArray())

        readString("key")

        assertThat(readString("key")).isEqualTo("plaintext")
    }

    @Test
    fun `put stores a value readable through get`() {
        store.put("key", "value".toByteArray())

        assertThat(readString("key")).isEqualTo("value")
    }

    @Test
    fun `put throws StorageFailed when the keystore call fails`() {
        // The shape production builds: the provider wraps every write failure in a
        // SecureStorageError before it leaves put(), so a raw exception never gets here.
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnPut(secureStorageError())
        )

        assertThatThrownBy { brokenStore.put("key", "value".toByteArray()) }
            .isInstanceOf(SecureKeyValueStoreError.StorageFailed::class.java)
    }

    @Test
    fun `put throws StorageFailed on an inconsistent storage mode`() {
        // Given: a value written under one mode and the device now reporting another, which is
        // the state the provider's own compatibility check looks for.
        store.put("key", "value".toByteArray())
        preferences.setMode(SecureStorageMode.J_MODE)

        assertThatThrownBy { store.put("key", "value".toByteArray()) }
            .isInstanceOf(SecureKeyValueStoreError.StorageFailed::class.java)
    }

    @Test
    fun `put throws StorageFailed when the mode check itself fails`() {
        // isCompatibleFormat() runs before the write is wrapped, so a corrupt mode preference
        // leaves put() as a raw IllegalArgumentException from SecureStorageMode.valueOf.
        val brokenStore = SecureKeyValueStore(
            providerThrowingOnPut(IllegalArgumentException("No enum constant"))
        )

        assertThatThrownBy { brokenStore.put("key", "value".toByteArray()) }
            .isInstanceOf(SecureKeyValueStoreError.StorageFailed::class.java)
    }

    @Test
    fun `has returns true for a stored key`() {
        secureStorageProvider.put("key", "value".toByteArray())

        assertThat(store.has("key")).isTrue()
    }

    @Test
    fun `has returns false for a missing key`() {
        assertThat(store.has("missing")).isFalse()
    }

    @Test
    fun `has returns true when the keystore entry is gone`() {
        // Given: ciphertext survives but the encryption key is lost (device restored from
        // backup). The record exists; only get can tell that it's unreadable.
        secureStorageProvider.put("key", "value".toByteArray())
        keystore.deleteEntry("key")

        assertThat(store.has("key")).isTrue()
    }

    @Test
    fun `has returns true when only an orphan keystore entry remains`() {
        // Given: the ciphertext was erased but the keystore entry survived (the frequent
        // partial-delete case that makes the legacy provider's has() throw). Answering false
        // would let erasing the record turn off whatever it gates, the lock screen included.
        secureStorageProvider.put("key", "value".toByteArray())
        preferences.delete("key")

        assertThat(store.has("key")).isTrue()
    }

    @Test
    fun `get reports an erased record as a storage failure`() {
        // The other half of the case above: has() sends the caller to the read, and the read
        // is what says the store is damaged rather than empty.
        secureStorageProvider.put("key", "value".toByteArray())
        preferences.delete("key")

        assertThatThrownBy { store.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.StorageFailed::class.java)
    }

    @Test
    fun `has wraps an unexpected provider failure in OperationFailed`() {
        val brokenStore = SecureKeyValueStore(providerThrowingRawExceptions())

        assertThatThrownBy { brokenStore.has("key") }
            .isInstanceOf(SecureKeyValueStoreError.OperationFailed::class.java)
    }

    @Test
    fun `has still answers when the orphan keystore probe throws`() {
        // Given: no ciphertext, and a keystore that can't even be asked. A probe that failed is
        // not evidence of a record, and has() runs before any Activity exists, so it answers
        // rather than throwing.
        val brokenKeystore = object : FakeKeyStore() {
            override fun hasKey(keyAlias: String): Boolean {
                throw MuunKeyStoreException(RuntimeException("boom"))
            }
        }
        val brokenStore =
            SecureKeyValueStore(SecureStorageProvider(brokenKeystore, FakePreferences()))

        assertThat(brokenStore.has("missing")).isFalse()
    }

    @Test
    fun `has still answers when the orphan report cannot build its snapshot`() {
        // Given: an orphan keystore entry, so the report runs, and a MODE preference the
        // snapshot can't parse. The answer comes from the keystore probe, so failing telemetry
        // must neither change it nor crash: has() runs before any Activity exists.
        //
        // The write seeds the shared keystore; the empty preferences below are what make the
        // entry an orphan.
        secureStorageProvider.put("key", "value".toByteArray())
        val brokenPreferences = object : FakePreferences() {
            override fun isCompatibleFormat(): Boolean {
                throw IllegalArgumentException("No enum constant")
            }
        }
        val brokenStore = SecureKeyValueStore(SecureStorageProvider(keystore, brokenPreferences))

        assertThat(brokenStore.has("key")).isTrue()
    }

    @Test
    fun `delete removes the stored value`() {
        secureStorageProvider.put("key", "value".toByteArray())

        store.delete("key")

        assertThatThrownBy { store.get("key").withSecret { } }
            .isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
    }

    @Test
    fun `delete throws OperationFailed when the keystore call fails`() {
        val brokenStore = SecureKeyValueStore(providerThrowingRawExceptions())

        assertThatThrownBy { brokenStore.delete("key") }
            .isInstanceOf(SecureKeyValueStoreError.OperationFailed::class.java)
    }

    @Test
    fun `wipe completes on success`() {
        store.wipe()
    }

    @Test
    fun `wipe throws OperationFailed when the keystore call fails`() {
        val brokenStore = SecureKeyValueStore(providerThrowingRawExceptions())

        assertThatThrownBy { brokenStore.wipe() }
            .isInstanceOf(SecureKeyValueStoreError.OperationFailed::class.java)
    }

    private fun readString(key: String): String {
        var seen: String? = null
        store.get(key).withSecret { bytes -> seen = String(bytes) }
        return seen!!
    }

    private fun providerThrowingOnGet(error: Throwable): SecureStorageProvider {
        val provider = mockk<SecureStorageProvider>()
        every { provider.get("key") } throws error
        return provider
    }

    private fun providerThrowingOnPut(error: Throwable): SecureStorageProvider {
        val provider = mockk<SecureStorageProvider>()
        every { provider.put(any(), any()) } throws error
        return provider
    }

    /**
     * The provider lets raw exceptions out of the methods stubbed here, unlike put(), which
     * wraps every write failure in a SecureStorageError before it leaves.
     */
    private fun providerThrowingRawExceptions(): SecureStorageProvider {
        val provider = mockk<SecureStorageProvider>()
        every { provider.hasCiphertext(any()) } throws RuntimeException("boom")
        every { provider.delete(any()) } throws RuntimeException("boom")
        every { provider.wipe() } throws RuntimeException("boom")
        return provider
    }
}
