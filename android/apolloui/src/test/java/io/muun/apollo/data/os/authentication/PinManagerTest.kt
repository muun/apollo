package io.muun.apollo.data.os.authentication

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.domain.libwallet.LibwalletClient
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

    lateinit var secureStorageProvider: SecureStorageProvider

    @MockK
    lateinit var libwalletClient: LibwalletClient

    private lateinit var pinManager: PinManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        secureStorageProvider = SecureStorageProvider(
            FakeKeyStore(),
            FakePreferences(),
        )

        pinManager = PinManager(secureStorageProvider, libwalletClient)
    }

    @Test
    fun set_and_has() {
        assertFalse(pinManager.hasPin())

        pinManager.storePin(PIN_1)

        assertTrue(pinManager.hasPin())
    }

    @Test
    fun validate_pin() {
        pinManager.storePin(PIN_1)

        assertFalse(pinManager.verifyPin(PIN_2))
        assertTrue(pinManager.verifyPin(PIN_1))
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
