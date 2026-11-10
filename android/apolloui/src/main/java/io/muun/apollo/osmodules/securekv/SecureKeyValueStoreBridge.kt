package io.muun.apollo.osmodules.securekv

import app_provided_data.App_provided_data
import app_provided_data.SecureKvGetResponse
import app_provided_data.SecureKvResponse
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import javax.inject.Inject

/**
 * Gomobile adapter over [SecureKeyValueStore]: only Go calls this. Typed
 * errors are flattened into status codes because gomobile cannot marshal
 * exceptions.
 */
internal class SecureKeyValueStoreBridge @Inject constructor(
    private val store: SecureKeyValueStore,
) : app_provided_data.SecureKeyValueStore {

    override fun put(key: String, value: ByteArray): SecureKvResponse {
        val response = SecureKvResponse()
        try {
            store.put(key, value)
            response.statusCode = App_provided_data.SecureKvStatusOk
        } catch (e: SecureKeyValueStoreError) {
            response.statusCode = e.toStatusCode()
        } finally {
            // gomobile materialized `value` as a fresh JVM copy for this call, so
            // wiping cannot touch a caller-owned array.
            value.fill(0)
        }
        return response
    }

    override fun get(key: String): SecureKvGetResponse {
        val response = SecureKvGetResponse()
        try {
            // The setter copies into Go-owned memory (gomobile Seq proxy): the
            // Secret wipes our copy on exit and Go wipes its own.
            store.get(key).withSecret { bytes ->
                response.value = bytes
                response.statusCode = App_provided_data.SecureKvStatusOk
            }
        } catch (e: SecureKeyValueStoreError) {
            response.statusCode = e.toStatusCode()
        }
        return response
    }

    override fun delete(key: String): SecureKvResponse {
        val response = SecureKvResponse()
        response.statusCode = try {
            store.delete(key)
            App_provided_data.SecureKvStatusOk
        } catch (e: SecureKeyValueStoreError) {
            e.toStatusCode()
        }
        return response
    }

    override fun wipe(): SecureKvResponse {
        val response = SecureKvResponse()
        response.statusCode = try {
            store.wipe()
            App_provided_data.SecureKvStatusOk
        } catch (e: SecureKeyValueStoreError) {
            e.toStatusCode()
        }
        return response
    }
}

// Watch out when adding an error: a new direct subtype of SecureKeyValueStoreError breaks this
// when and forces a decision, but a subtype of one already listed here (KeystoreKeyMissing under
// DecryptionFailed) silently inherits its status. Check that the inherited one is the right answer.
internal fun SecureKeyValueStoreError.toStatusCode(): Int {
    return when (this) {
        is SecureKeyValueStoreError.NotFound ->
            App_provided_data.SecureKvStatusNotFound
        is SecureKeyValueStoreError.DecryptionFailed ->
            App_provided_data.SecureKvStatusDecryptionFailed
        is SecureKeyValueStoreError.StorageFailed ->
            App_provided_data.SecureKvStatusStorageFailed
        // The split is consumed natively, by the corruption predicate in Extensions.kt. Only
        // the Go path loses it, and securekv has no production consumer there yet. Carrying it
        // across needs a new status code plus a change in securekv's Put, Delete and Wipe,
        // which collapse every non-Ok status into StorageFailedError. Until then this reports
        // what these failures already reported before the split.
        is SecureKeyValueStoreError.OperationFailed ->
            App_provided_data.SecureKvStatusStorageFailed
    }
}
