package io.muun.apollo.osmodules.securekv

import app_provided_data.App_provided_data
import io.muun.apollo.BaseTest
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SecureKeyValueStoreBridgeTest : BaseTest() {

    // The bridge methods build gomobile response proxies, which need the native
    // library and cannot run in JVM tests: only the error mapping is covered here.

    @Test
    fun `NotFound maps to the NotFound status code`() {
        val statusCode = SecureKeyValueStoreError.NotFound().toStatusCode()

        assertThat(statusCode).isEqualTo(App_provided_data.SecureKvStatusNotFound)
    }

    @Test
    fun `DecryptionFailed maps to the DecryptionFailed status code`() {
        val statusCode = SecureKeyValueStoreError.DecryptionFailed(Exception()).toStatusCode()

        assertThat(statusCode).isEqualTo(App_provided_data.SecureKvStatusDecryptionFailed)
    }

    @Test
    fun `StorageFailed maps to the StorageFailed status code`() {
        val statusCode = SecureKeyValueStoreError.StorageFailed(Exception()).toStatusCode()

        assertThat(statusCode).isEqualTo(App_provided_data.SecureKvStatusStorageFailed)
    }

    @Test
    fun `OperationFailed maps to the StorageFailed status code`() {
        // Deliberate loss of information: Go has no retryable status yet. Pinned so that
        // adding one is a decision rather than something the next reader assumes happened.
        val statusCode = SecureKeyValueStoreError.OperationFailed(Exception()).toStatusCode()

        assertThat(statusCode).isEqualTo(App_provided_data.SecureKvStatusStorageFailed)
    }
}
