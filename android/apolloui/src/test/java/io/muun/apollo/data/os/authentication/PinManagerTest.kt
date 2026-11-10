package io.muun.apollo.data.os.authentication

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.osmodules.securekv.SecureKeyValueStore
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinManagerTest : BaseTest() {

    companion object {
        const val PIN_1 = "1234"
        const val PIN_2 = "1111"
    }

    @MockK
    lateinit var libwalletClient: LibwalletClient

    private lateinit var keystore: FakeKeyStore
    private lateinit var pinManager: PinManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        keystore = FakeKeyStore()
        val provider = SecureStorageProvider(keystore, FakePreferences())

        val repository = SecureKeyValueStoreRepository(SecureKeyValueStore(provider))
        pinManager = PinManager(repository, libwalletClient)
    }

    @Test
    fun set_and_has() {
        assertFalse(pinManager.hasPin())

        pinManager.storePin(PIN_1)

        assertTrue(pinManager.hasPin())
    }

    @Test
    fun has_pin_answers_when_the_keystore_is_empty() {
        // The shape an OEM device-to-device copy leaves behind: the preferences travel and the
        // keystore keys do not. isLockConfigured() asks this from Application#onCreate, before
        // any Activity exists, so a throw here is a crash with no dialog to land in.
        pinManager.storePin(PIN_1)
        keystore.wipe()

        assertTrue(pinManager.hasPin())
    }

    @Test
    fun validate_pin() {
        pinManager.storePin(PIN_1)

        assertFalse(pinManager.verifyPin(PIN_2))
        assertTrue(pinManager.verifyPin(PIN_1))
    }

    @Test
    fun verify_without_stored_pin_throws_not_found() {
        assertThatThrownBy { pinManager.verifyPin(PIN_1) }
            .isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
    }

    @Test
    fun can_change_pin() {
        pinManager.storePin(PIN_1)
        pinManager.storePin(PIN_2)

        assertFalse(pinManager.verifyPin(PIN_1))
        assertTrue(pinManager.verifyPin(PIN_2))
    }

    @Test
    fun get_pin_length_should_return_users_pin_length_for_new_users() {
        every { libwalletClient.getInt(any()) } returns 6
        every { libwalletClient.getInt(any(), any()) } answers { callOriginal() }

        val pinLength = pinManager.pinLength

        assertEquals(6, pinLength)
    }

    @Test
    fun get_pin_length_should_return_legacy_4digit_for_existing_users() {
        every { libwalletClient.getInt(any()) } returns null
        every { libwalletClient.getInt(any(), any()) } answers { callOriginal() }

        val pinLength = pinManager.pinLength

        assertEquals(4, pinLength)
    }
}
