package io.muun.apollo.data.net.base

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession
import timber.log.Timber

/**
 * Verifies the hostname with [delegate], then rejects sessions whose certificate chain carries a
 * distrusted key (see [DistrustedChainCheck]). Runs after the handshake and before the request is
 * sent, so it covers HTTP clients that have no OkHttp interceptors, like the Google API client.
 */
class DistrustedCertificateHostnameVerifier(
    private val delegate: HostnameVerifier,
    private val chainCheck: DistrustedChainCheck,
) : HostnameVerifier {

    companion object {

        fun withPlatformTrust(): DistrustedCertificateHostnameVerifier {
            return DistrustedCertificateHostnameVerifier(
                HttpsURLConnection.getDefaultHostnameVerifier(),
                DistrustedChainCheck.withPlatformTrust()
            )
        }
    }

    override fun verify(hostname: String, session: SSLSession): Boolean {
        if (!delegate.verify(hostname, session)) {
            return false
        }

        return try {
            chainCheck.check(session.peerCertificates.toList(), hostname)
            true
        } catch (e: Exception) {
            // HttpsURLConnection only reports a generic hostname failure, so keep the reason.
            Timber.e(e, "Rejecting %s: distrusted or unverifiable certificate chain", hostname)
            false
        }
    }
}
