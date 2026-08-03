package io.muun.apollo.domain.secure_key_value_storage

import io.muun.apollo.BaseTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test

class SecretTest : BaseTest() {

    @Test
    fun `should invoke fn with plaintext bytes`() {
        // Given
        val expected = listOf(1.toByte(), 2.toByte(), 3.toByte())
        val secret = Secret(byteArrayOf(1, 2, 3))

        // When
        secret.withSecret { bytes ->
            // Then
            assertThat(bytes.toList()).isEqualTo(expected)
        }
    }

    @Test
    fun `should zero the wrapped bytes after withSecret returns`() {
        // Given
        val payload = byteArrayOf(1, 2, 3)
        val expected = byteArrayOf(0, 0, 0)
        val secret = Secret(payload)

        // When
        secret.withSecret { /* consume */ }

        // Then
        assertThat(payload).isEqualTo(expected)
    }

    @Test
    fun `should zero the wrapped bytes when fn throws`() {
        // Given
        val payload = byteArrayOf(1, 2, 3)
        val expected = byteArrayOf(0, 0, 0)
        val secret = Secret(payload)

        // When
        val actual = catchThrowable {
            secret.withSecret { throw RuntimeException("boom") }
        }

        // Then
        assertThat(actual).hasMessage("boom")
        assertThat(payload).isEqualTo(expected)
    }

    @Test
    fun `should throw on second withSecret call`() {
        // Given
        val secret = Secret(byteArrayOf(1, 2, 3))
        secret.withSecret { /* first call consumes the Secret */ }

        // When
        val actual = catchThrowable {
            secret.withSecret { /* second call must fail */ }
        }

        // Then
        assertThat(actual)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Secret already consumed")
    }
}
