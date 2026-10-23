package io.muun.apollo.data.net.base

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.security.cert.Certificate
import java.security.cert.CertificateException
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

class DistrustedCertificateHostnameVerifierTest {

    companion object {
        private const val HOST = "www.googleapis.com"
    }

    private val emudhraIntermediates = TestCertificates.emudhraIntermediates
    private val houstonChain = TestCertificates.houstonChain

    private val acceptingDelegate = HostnameVerifier { _, _ -> true }

    @Test
    fun `should reject when the delegate rejects the hostname`() {
        // GIVEN a delegate that rejects the hostname, and a clean chain
        val verifier = DistrustedCertificateHostnameVerifier(
            HostnameVerifier { _, _ -> false },
            DistrustedChainCheck { served, _ -> served }
        )
        val session = sessionServing(houstonChain)

        // WHEN the session is verified
        val actual = verifier.verify(HOST, session)

        // THEN it's rejected
        assertThat(actual).isFalse()
    }

    @Test
    fun `should reject when only the verified chain carries a distrusted key`() {
        // GIVEN a server that sends just the leaf, and Android completing the chain with a
        // compromised intermediate
        val verifier = verifierVerifyingAs(listOf(houstonChain[0], emudhraIntermediates[2]))
        val session = sessionServing(listOf(houstonChain[0]))

        // WHEN the session is verified
        val actual = verifier.verify(HOST, session)

        // THEN it's rejected
        assertThat(actual).isFalse()
    }

    @Test
    fun `should reject when the chain can't be verified`() {
        // GIVEN a chain the platform can't verify
        val verifier = DistrustedCertificateHostnameVerifier(
            acceptingDelegate,
            DistrustedChainCheck { _, _ -> throw CertificateException("no trust anchor") }
        )
        val session = sessionServing(houstonChain)

        // WHEN the session is verified
        val actual = verifier.verify(HOST, session)

        // THEN it's rejected
        assertThat(actual).isFalse()
    }

    @Test
    fun `should reject an unverified session`() {
        // GIVEN a session with no verified peer certificates
        val session: SSLSession = mockk {
            every { peerCertificates } throws SSLPeerUnverifiedException("peer not verified")
        }
        val verifier = verifierVerifyingAs(houstonChain)

        // WHEN the session is verified
        val actual = verifier.verify(HOST, session)

        // THEN it's rejected
        assertThat(actual).isFalse()
    }

    @Test
    fun `should verify against the request hostname`() {
        // GIVEN a chain check that records the host it's asked about
        var verifiedHost: String? = null
        val verifier = DistrustedCertificateHostnameVerifier(
            acceptingDelegate,
            DistrustedChainCheck { served, host ->
                verifiedHost = host
                served
            }
        )

        // WHEN a session for that host is verified
        verifier.verify(HOST, sessionServing(houstonChain))

        // THEN the chain is checked for that host
        assertThat(verifiedHost).isEqualTo(HOST)
    }

    @Test
    fun `should accept a clean chain`() {
        // GIVEN houston's real chain
        val verifier = verifierVerifyingAs(houstonChain)
        val session = sessionServing(houstonChain)

        // WHEN the session is verified
        val actual = verifier.verify(HOST, session)

        // THEN it's accepted
        assertThat(actual).isTrue()
    }

    private fun verifierVerifyingAs(
        verified: List<Certificate>,
    ): DistrustedCertificateHostnameVerifier {
        return DistrustedCertificateHostnameVerifier(
            acceptingDelegate,
            DistrustedChainCheck { _, _ -> verified }
        )
    }

    private fun sessionServing(certificates: List<Certificate>): SSLSession {
        return mockk { every { peerCertificates } returns certificates.toTypedArray() }
    }
}
