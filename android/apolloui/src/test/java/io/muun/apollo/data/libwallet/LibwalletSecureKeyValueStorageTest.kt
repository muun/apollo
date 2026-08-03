package io.muun.apollo.data.libwallet

import app_provided_data.App_provided_data
import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class LibwalletSecureKeyValueStorageTest : BaseTest() {

    private lateinit var keystore: FakeKeyStore
    private lateinit var preferences: FakePreferences
    private lateinit var secureStorageProvider: SecureStorageProvider
    private lateinit var bridge: LibwalletSecureKeyValueStorage

    @Before
    fun setUp() {
        keystore = FakeKeyStore()
        preferences = FakePreferences()
        secureStorageProvider = SecureStorageProvider(keystore, preferences)
        bridge = LibwalletSecureKeyValueStorage(secureStorageProvider)
    }

    @Test
    fun `get reports NotFound when key is missing`() {
        val (value, status) = bridge.getValueAndStatus("missing")
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusNotFound)
        assertThat(value).isEmpty()
    }

    @Test
    fun `get reports DecryptionFailed when keystore entry is gone`() {
        // Given: key was stored, then the keystore entry is removed (simulates a
        // lock-screen change wiping the encryption key while ciphertext stays in prefs).
        secureStorageProvider.put("key", "value".toByteArray())
        keystore.deleteEntry("key")

        val (_, status) = bridge.getValueAndStatus("key")
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusDecryptionFailed)
    }

    @Test
    fun `get reports Ok and returns plaintext for a stored key`() {
        secureStorageProvider.put("key", "plaintext".toByteArray())

        val (value, status) = bridge.getValueAndStatus("key")
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusOk)
        assertThat(String(value)).isEqualTo("plaintext")
    }

    @Test
    fun `put reports Ok on success`() {
        val status = bridge.putStatus("key", "value".toByteArray())
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusOk)
    }

    @Test
    fun `put reports StorageFailed when provider throws`() {
        val brokenBridge = LibwalletSecureKeyValueStorage(throwingProvider())

        val status = brokenBridge.putStatus("key", "value".toByteArray())
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusStorageFailed)
    }

    @Test
    fun `delete reports Ok on success`() {
        secureStorageProvider.put("key", "value".toByteArray())

        val status = bridge.deleteStatus("key")
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusOk)
    }

    @Test
    fun `delete reports StorageFailed when provider throws`() {
        val brokenBridge = LibwalletSecureKeyValueStorage(throwingProvider())

        val status = brokenBridge.deleteStatus("key")
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusStorageFailed)
    }

    @Test
    fun `wipe reports Ok on success`() {
        val status = bridge.wipeStatus()
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusOk)
    }

    @Test
    fun `wipe reports StorageFailed when provider throws`() {
        val brokenBridge = LibwalletSecureKeyValueStorage(throwingProvider())

        val status = brokenBridge.wipeStatus()
        assertThat(status).isEqualTo(App_provided_data.SecureKvStatusStorageFailed)
    }

    private fun throwingProvider(): SecureStorageProvider {
        val provider = mockk<SecureStorageProvider>()
        every { provider.put(any(), any()) } throws RuntimeException("boom")
        every { provider.delete(any()) } throws RuntimeException("boom")
        every { provider.wipe() } throws RuntimeException("boom")
        return provider
    }
}
