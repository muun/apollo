package io.muun.apollo.data.net.base

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class TlsVerificationErrorTest {

    @Test
    fun `pin or hostname mismatch should be a trust failure`() {
        // GIVEN a pin or hostname mismatch
        val error = SSLPeerUnverifiedException("Certificate pinning failure!")

        // WHEN it's classified
        val actual = TlsVerificationError.isTlsTrustFailure(error)

        // THEN it's a trust failure
        assertThat(actual).isTrue()
    }

    @Test
    fun `untrusted chain should be a trust failure`() {
        // GIVEN Android's "Trust anchor for certification path not found" shape
        val error = SSLHandshakeException("handshake failed").apply {
            initCause(CertificateException(CertPathValidatorException("no trust anchor")))
        }

        // WHEN it's classified
        val actual = TlsVerificationError.isTlsTrustFailure(error)

        // THEN it's a trust failure
        assertThat(actual).isTrue()
    }

    @Test
    fun `expired certificate should be a trust failure`() {
        // GIVEN a handshake that failed on an expired certificate
        val error = SSLHandshakeException("handshake failed").apply {
            initCause(CertificateExpiredException("expired"))
        }

        // WHEN it's classified
        val actual = TlsVerificationError.isTlsTrustFailure(error)

        // THEN it's a trust failure
        assertThat(actual).isTrue()
    }

    @Test
    fun `handshake failed for transport reasons should not be a trust failure`() {
        // GIVEN a handshake that failed because the connection was reset
        val error = SSLHandshakeException("connection closed").apply {
            initCause(SocketException("Connection reset"))
        }

        // WHEN it's classified
        val actual = TlsVerificationError.isTlsTrustFailure(error)

        // THEN it isn't a trust failure
        assertThat(actual).isFalse()
    }

    @Test
    fun `other SSL and IO errors should not be trust failures`() {
        // GIVEN SSL and IO errors unrelated to the certificate
        val sslError = SSLException("read error")
        val ioError = IOException("io")

        // WHEN they're classified
        val actualForSsl = TlsVerificationError.isTlsTrustFailure(sslError)
        val actualForIo = TlsVerificationError.isTlsTrustFailure(ioError)

        // THEN neither is a trust failure
        assertThat(actualForSsl).isFalse()
        assertThat(actualForIo).isFalse()
    }
}
