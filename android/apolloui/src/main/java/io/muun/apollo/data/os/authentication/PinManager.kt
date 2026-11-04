package io.muun.apollo.data.os.authentication

import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.common.utils.Encodings
import javax.inject.Inject

// Open for mockito to mock/spy.
open class PinManager @Inject constructor(
    private val secureKeyValueStoreRepository: SecureKeyValueStoreRepository,
    private val libwalletClient: LibwalletClient,
) {

    companion object {
        private const val PIN_KEY = "pin"
        private const val PIN_LENGTH_KEY = "pinLength"
        private const val LEGACY_PIN_LENGTH = 4
    }

    // Old app versions never saved PIN_LENGTH_KEY: their pins are LEGACY_PIN_LENGTH long.
    open val pinLength: Int
        get() = libwalletClient.getInt(PIN_LENGTH_KEY, LEGACY_PIN_LENGTH)

    open fun storePin(pin: String) {
        val pinBytes = Encodings.stringToBytes(pin)

        try {
            secureKeyValueStoreRepository.put(PIN_KEY, pinBytes)
        } finally {
            // The input copy is plaintext pin material too.
            pinBytes.fill(0)
        }

        libwalletClient.saveInt(PIN_LENGTH_KEY, pin.length)
    }

    // Deciding whether to show the lock screen is the check-only use the store sanctions: it
    // has to answer without touching plaintext. The deprecation is counting this caller, which
    // goes away with the move to libwallet.
    @Suppress("DEPRECATION")
    open fun hasPin(): Boolean {
        return secureKeyValueStoreRepository.has(PIN_KEY)
    }

    /**
     * Report whether [pin] is the stored one. Throws [SecureKeyValueStoreError.NotFound] when
     * no pin was ever stored, which is not the same answer as false.
     */
    open fun verifyPin(pin: String): Boolean {
        val pinBytes = Encodings.stringToBytes(pin)
        var matches = false

        try {
            secureKeyValueStoreRepository.get(PIN_KEY).withSecret { storedPinBytes ->
                matches = storedPinBytes.contentEquals(pinBytes)
            }
        } finally {
            // The input copy is plaintext pin material too.
            pinBytes.fill(0)
        }

        return matches
    }
}
