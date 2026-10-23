package io.muun.apollo.data.net.base.interceptor

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.data.net.base.DistrustedChainCheck
import io.muun.apollo.data.net.base.TestCertificates
import okhttp3.CipherSuite
import okhttp3.Connection
import okhttp3.Handshake
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import java.security.cert.Certificate
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

class DistrustedCertificateInterceptorTest {

    private val emudhraIntermediates = TestCertificates.emudhraIntermediates
    private val houstonChain = TestCertificates.houstonChain
    private val houstonLeaf = houstonChain[0]

    private val request = Request.Builder().url("https://pub.api.muun.io/houston/user").build()
    private val response: Response = mockk()

    @Test
    fun `should fail before sending when the served chain carries a distrusted key`() {
        // GIVEN a server that sends a compromised intermediate
        val chain = chainServing(listOf(houstonLeaf, emudhraIntermediates[0]))
        val interceptor = interceptorVerifyingAs(houstonChain)

        // WHEN a request goes through
        val error = catchThrowable { interceptor.intercept(chain) }

        // THEN it fails and nothing is sent
        assertThat(error).isInstanceOf(SSLPeerUnverifiedException::class.java)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `should fail when only the verified chain carries a distrusted key`() {
        // GIVEN a server that sends just the leaf, and Android completing the chain with an
        // intermediate cached from an earlier handshake
        val chain = chainServing(listOf(houstonLeaf))
        val interceptor = interceptorVerifyingAs(listOf(houstonLeaf, emudhraIntermediates[1]))

        // WHEN a request goes through
        val error = catchThrowable { interceptor.intercept(chain) }

        // THEN it fails and nothing is sent
        assertThat(error).isInstanceOf(SSLPeerUnverifiedException::class.java)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `should fail closed when the chain can't be verified`() {
        // GIVEN a chain the platform can't verify
        val chain = chainServing(houstonChain)
        val interceptor = interceptorWith { _, _ ->
            throw CertificateException("Trust anchor for certification path not found.")
        }

        // WHEN a request goes through
        val error = catchThrowable { interceptor.intercept(chain) }

        // THEN it fails with the cause and nothing is sent
        assertThat(error).isInstanceOf(SSLPeerUnverifiedException::class.java)
        assertThat(error.cause).isInstanceOf(CertificateException::class.java)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `should verify against the request host`() {
        // GIVEN a verifier that records the host it's asked about
        var verifiedHost: String? = null
        val interceptor = interceptorWith { served, host ->
            verifiedHost = host
            served
        }

        // WHEN a request to houston goes through
        interceptor.intercept(chainServing(houstonChain))

        // THEN the chain is verified for houston's host
        assertThat(verifiedHost).isEqualTo("pub.api.muun.io")
    }

    @Test
    fun `should proceed with houston's chain`() {
        // GIVEN houston's real chain
        val chain = chainServing(houstonChain)
        val interceptor = interceptorVerifyingAs(houstonChain)

        // WHEN a request goes through
        val actual = interceptor.intercept(chain)

        // THEN it's sent
        assertThat(actual).isSameAs(response)
    }

    @Test
    fun `should check each connection's chain once`() {
        // GIVEN a connection whose chain already passed the check on an earlier request
        var verifications = 0
        val interceptor = interceptorWith { served, _ ->
            verifications++
            served
        }
        val chain = chainServing(houstonChain)
        interceptor.intercept(chain)

        // WHEN another request goes through the same connection
        val actual = interceptor.intercept(chain)

        // THEN it's sent without rebuilding the chain again
        assertThat(actual).isSameAs(response)
        assertThat(verifications).isEqualTo(1)
    }

    @Test
    fun `should proceed on a plain http connection`() {
        // GIVEN a connection with no TLS handshake
        val connection: Connection = mockk { every { handshake() } returns null }
        val interceptor = interceptorWith { _, _ ->
            throw AssertionError("Plain http has nothing to verify")
        }

        // WHEN a request goes through
        val actual = interceptor.intercept(chainOn(connection))

        // THEN it's sent
        assertThat(actual).isSameAs(response)
    }

    @Test
    fun `should refuse to run as an application interceptor`() {
        // GIVEN an application interceptor chain, which has no connection: the check would
        // silently do nothing
        val chain: Interceptor.Chain = mockk {
            every { connection() } returns null
            every { request() } returns request
        }
        val interceptor = interceptorVerifyingAs(houstonChain)

        // WHEN a request goes through
        val error = catchThrowable { interceptor.intercept(chain) }

        // THEN it fails and nothing is sent
        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    private fun interceptorVerifyingAs(
        verified: List<Certificate>,
    ): DistrustedCertificateInterceptor {
        return interceptorWith { _, _ -> verified }
    }

    private fun interceptorWith(
        verifiedChain: (List<Certificate>, String) -> List<Certificate>,
    ): DistrustedCertificateInterceptor {
        return DistrustedCertificateInterceptor(DistrustedChainCheck(verifiedChain))
    }

    private fun chainServing(peerCertificates: List<Certificate>): Interceptor.Chain {
        val handshake = Handshake.get(
            TlsVersion.TLS_1_2,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            peerCertificates,
            emptyList()
        )
        val connection: Connection = mockk { every { handshake() } returns handshake }

        return chainOn(connection)
    }

    private fun chainOn(connection: Connection): Interceptor.Chain {
        return mockk {
            every { connection() } returns connection
            every { request() } returns request
            every { proceed(request) } returns response
        }
    }
}
