package io.muun.apollo.data.net.base

import io.mockk.every
import io.mockk.mockk
import io.muun.common.net.NetworkRetry
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Invocation
import rx.Observable
import rx.observers.TestSubscriber
import java.lang.reflect.Type
import java.net.SocketException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class RxCallAdapterWrapperTest {

    companion object {
        private const val MAX_RETRIES = 2
        private const val URL = "https://houston.example/houston/user"
    }

    private interface FakeService {
        @NetworkRetry(count = MAX_RETRIES, baseIntervalInMs = 1)
        fun fetch(): Observable<String>
    }

    @Test
    fun `pin or hostname mismatch should fail as TlsVerificationError`() {
        // GIVEN a pin or hostname mismatch
        val cause = SSLPeerUnverifiedException("Certificate pinning failure!")

        // WHEN a request fails with it
        val (error, attempts) = requestFailingWith(cause)

        // THEN it's retried and fails as a TlsVerificationError with the URL and the cause
        assertThat(error).isInstanceOf(TlsVerificationError::class.java)
        assertThat(error).hasMessageContaining(URL)
        assertThat(generateSequence(error) { it.cause }.toList()).contains(cause)
        assertThat(attempts).isEqualTo(1 + MAX_RETRIES)
    }

    @Test
    fun `untrusted chain should fail as TlsVerificationError`() {
        // GIVEN a handshake that failed on an untrusted chain
        val cause = SSLHandshakeException("handshake failed").apply {
            initCause(CertificateException(CertPathValidatorException("no trust anchor")))
        }

        // WHEN a request fails with it
        val (error, attempts) = requestFailingWith(cause)

        // THEN it's retried and fails as a TlsVerificationError
        assertThat(error).isInstanceOf(TlsVerificationError::class.java)
        assertThat(attempts).isEqualTo(1 + MAX_RETRIES)
    }

    @Test
    fun `transport handshake failure should stay a plain NetworkException`() {
        // GIVEN a handshake that failed because the connection was reset
        val cause = SSLHandshakeException("connection closed").apply {
            initCause(SocketException("Connection reset"))
        }

        // WHEN a request fails with it
        val (error, attempts) = requestFailingWith(cause)

        // THEN it's retried and fails as a plain NetworkException
        assertThat(error).isExactlyInstanceOf(NetworkException::class.java)
        assertThat(attempts).isEqualTo(1 + MAX_RETRIES)
    }

    private fun requestFailingWith(cause: Throwable): Pair<Throwable, Int> {
        var attempts = 0
        val wrapped = object : CallAdapter<String, Observable<String>> {
            override fun responseType(): Type {
                return String::class.java
            }

            override fun adapt(call: Call<String>): Observable<String> {
                return Observable.defer {
                    attempts++
                    Observable.error<String>(cause)
                }
            }
        }

        val invocation = Invocation.of(FakeService::class.java.getMethod("fetch"), emptyList<Any>())
        val request = Request.Builder()
            .url(URL)
            .tag(Invocation::class.java, invocation)
            .build()
        val call = mockk<Call<String>> {
            every { request() } returns request
        }

        val subscriber = TestSubscriber<String>()
        @Suppress("UNCHECKED_CAST")
        (RxCallAdapterWrapper(wrapped, HashMap(), false, false).adapt(call) as Observable<String>)
            .subscribe(subscriber)

        subscriber.awaitTerminalEvent()
        return Pair(subscriber.onErrorEvents.single(), attempts)
    }
}
