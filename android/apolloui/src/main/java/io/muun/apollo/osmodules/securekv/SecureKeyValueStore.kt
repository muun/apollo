package io.muun.apollo.osmodules.securekv

import io.muun.apollo.data.os.secure_storage.MuunKeyStoreException
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.InconsistentModeError
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.KeyStoreCorruptedError
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.SecureStorageNoSuchElementError
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.SharedPreferencesCorruptedError
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.errors.SecureStorageError
import timber.log.Timber
import javax.inject.Inject

/**
 * Native core over the legacy secure storage: knows nothing about gomobile.
 */
internal class SecureKeyValueStore @Inject constructor(
    // TODO: SecureStorageProvider is legacy and is going to be migrated to kotlin
    // in a future modernization.
    private val secureStorageProvider: SecureStorageProvider,
) {

    fun put(key: String, value: ByteArray) {
        try {
            secureStorageProvider.put(key, value)
        } catch (e: Exception) {
            // A write needs its keystore key to work, and encryptData only regenerates the
            // key when the alias is missing. So a broken one gets reused on every retry, and
            // the provider gives us no way to tell that apart from a transient failure.
            Timber.e(e)
            throw SecureKeyValueStoreError.StorageFailed(e)
        }
    }

    fun get(key: String): Secret {
        return Secret(key, ::fetchBytes)
    }

    /**
     * Report whether a value is stored under [key], without decrypting anything.
     *
     * True means something was stored, and says nothing about whether [get] will manage to
     * read it back. Half a record counts: a device restored from backup keeps the ciphertext
     * and loses the keystore key, which [get] reports as
     * [SecureKeyValueStoreError.KeystoreKeyMissing]; a half-finished delete leaves the keystore
     * key and loses the ciphertext, which [get] reports as
     * [SecureKeyValueStoreError.StorageFailed]. Both answer true so that the read is what
     * classifies the damage. Existence and readability are separate questions on purpose:
     * readability is answered by the [get] error taxonomy alone.
     *
     * Never use this as a gate before [get]. Consume [get] and branch on
     * [SecureKeyValueStoreError.NotFound] instead: this exists only for check-only callers
     * that decide UI state (e.g. whether a lock screen is configured) and must not touch
     * plaintext to do it.
     */
    fun has(key: String): Boolean {
        return try {
            secureStorageProvider.hasCiphertext(key)
        } catch (e: Exception) {
            Timber.e(e)
            throw SecureKeyValueStoreError.OperationFailed(e)
        }
    }

    fun delete(key: String) {
        try {
            secureStorageProvider.delete(key)
        } catch (e: Exception) {
            Timber.e(e)
            throw SecureKeyValueStoreError.OperationFailed(e)
        }
    }

    fun wipe() {
        try {
            secureStorageProvider.wipe()
        } catch (e: Exception) {
            Timber.e(e)
            throw SecureKeyValueStoreError.OperationFailed(e)
        }
    }

    private fun fetchBytes(key: String): ByteArray {
        return try {
            secureStorageProvider.get(key)
        } catch (e: SecureStorageNoSuchElementError) {
            throw SecureKeyValueStoreError.NotFound()
        } catch (e: SecureStorageError) {
            Timber.e(e)
            throw e.toReadError()
        } catch (e: MuunKeyStoreException) {
            // The keystore presence probe. It made no finding about the stored value.
            Timber.e(e)
            throw SecureKeyValueStoreError.OperationFailed(e)
        } catch (e: Exception) {
            // The mode check runs before the provider starts wrapping its failures, so a
            // corrupt MODE preference arrives here raw. It is a finding about the storage,
            // and the write path classifies it the same way.
            Timber.e(e)
            throw SecureKeyValueStoreError.StorageFailed(e)
        }
    }

    // Read-path mapping: DecryptionFailed means "the key material can no longer
    // open the value". Its dominant cause, a device-to-device migration where prefs
    // travel but keystore keys never do, gets the narrower KeystoreKeyMissing: that
    // is the only shape a caller can work around, by rewriting the value.
    private fun SecureStorageError.toReadError(): SecureKeyValueStoreError {
        return when (this) {
            // Ciphertext present, keystore key gone.
            is KeyStoreCorruptedError -> SecureKeyValueStoreError.KeystoreKeyMissing(this)
            // Storage in an unusable state: nothing decryptable about it.
            is InconsistentModeError,
            is SharedPreferencesCorruptedError,
            -> SecureKeyValueStoreError.StorageFailed(this)
            // In the read path the base type only comes from the decrypt step
            // (retrieveDecrypted wraps any failure): the value didn't decrypt.
            else -> SecureKeyValueStoreError.DecryptionFailed(this)
        }
    }
}
