package io.muun.apollo.data.libwallet

import androidx.annotation.VisibleForTesting
import app_provided_data.App_provided_data
import app_provided_data.SecureKeyValueStorage
import app_provided_data.SecureKvGetResponse
import app_provided_data.SecureKvResponse
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.SecureStorageNoSuchElementError
import io.muun.apollo.domain.errors.SecureStorageError
import timber.log.Timber
import javax.inject.Inject

class LibwalletSecureKeyValueStorage @Inject constructor(
    // TODO: SecureStorageProvider is legacy and is going to be migrated to kotlin
    // in a future modernization.
    private val secureStorageProvider: SecureStorageProvider,
) : SecureKeyValueStorage {

    override fun put(key: String, value: ByteArray): SecureKvResponse {
        return SecureKvResponse().apply { statusCode = putStatus(key, value) }
    }

    override fun get(key: String): SecureKvGetResponse {
        val (bytes, status) = getValueAndStatus(key)

        return SecureKvGetResponse().apply {
            value = bytes
            statusCode = status
        }
    }

    override fun delete(key: String): SecureKvResponse {
        return SecureKvResponse().apply { statusCode = deleteStatus(key) }
    }

    override fun wipe(): SecureKvResponse {
        return SecureKvResponse().apply { statusCode = wipeStatus() }
    }

    @VisibleForTesting
    internal fun putStatus(key: String, value: ByteArray): Int {
        return try {
            secureStorageProvider.put(key, value)
            App_provided_data.SecureKvStatusOk
        } catch (e: Exception) {
            Timber.e(e)
            App_provided_data.SecureKvStatusStorageFailed
        }
    }

    @VisibleForTesting
    internal fun getValueAndStatus(key: String): Pair<ByteArray, Int> {
        return try {
            val bytes = secureStorageProvider.get(key)
            bytes to App_provided_data.SecureKvStatusOk
        } catch (e: SecureStorageNoSuchElementError) {
            byteArrayOf() to App_provided_data.SecureKvStatusNotFound
        } catch (e: SecureStorageError) {
            Timber.e(e)
            byteArrayOf() to App_provided_data.SecureKvStatusDecryptionFailed
        } catch (e: Exception) {
            Timber.e(e)
            byteArrayOf() to App_provided_data.SecureKvStatusStorageFailed
        }
    }

    @VisibleForTesting
    internal fun deleteStatus(key: String): Int {
        return try {
            secureStorageProvider.delete(key)
            App_provided_data.SecureKvStatusOk
        } catch (e: Exception) {
            Timber.e(e)
            App_provided_data.SecureKvStatusStorageFailed
        }
    }

    @VisibleForTesting
    internal fun wipeStatus(): Int {
        return try {
            secureStorageProvider.wipe()
            App_provided_data.SecureKvStatusOk
        } catch (e: Exception) {
            Timber.e(e)
            App_provided_data.SecureKvStatusStorageFailed
        }
    }
}
