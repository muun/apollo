package io.muun.apollo.osmodules.securekv

import io.muun.apollo.BaseTest
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test

class SecretTest : BaseTest() {

    @Test
    fun `withSecret passes the bytes fetched for its key to the callback`() {
        val secret = Secret("key") { key ->
            if (key == "key") byteArrayOf(1, 2, 3) else byteArrayOf()
        }
        var seen: List<Byte>? = null

        secret.withSecret { bytes -> seen = bytes.toList() }

        assertThat(seen).isEqualTo(listOf(1.toByte(), 2.toByte(), 3.toByte()))
    }

    @Test
    fun `withSecret zeroes the buffer after the callback`() {
        val buffer = byteArrayOf(1, 2, 3)
        val secret = Secret("key") { buffer }

        secret.withSecret { }

        assertThat(buffer.toList()).containsOnly(0.toByte())
    }

    @Test
    fun `withSecret zeroes the buffer even when the callback throws`() {
        val buffer = byteArrayOf(1, 2, 3)
        val secret = Secret("key") { buffer }

        catchThrowable {
            secret.withSecret { error("boom") }
        }

        assertThat(buffer.toList()).containsOnly(0.toByte())
    }

    @Test
    fun `withSecret propagates the fetch error and skips the callback`() {
        val secret = Secret("key") { throw SecureKeyValueStoreError.NotFound() }
        var called = false

        val actual = catchThrowable {
            secret.withSecret { called = true }
        }

        assertThat(actual).isInstanceOf(SecureKeyValueStoreError.NotFound::class.java)
        assertThat(called).isFalse()
    }

    @Test
    fun `withSecret fetches fresh bytes on every call`() {
        var calls = 0.toByte()
        val secret = Secret("key") { byteArrayOf(++calls) }
        val seen = mutableListOf<Byte>()

        secret.withSecret { bytes -> seen.add(bytes[0]) }
        secret.withSecret { bytes -> seen.add(bytes[0]) }

        assertThat(seen).isEqualTo(listOf(1.toByte(), 2.toByte()))
    }
}
