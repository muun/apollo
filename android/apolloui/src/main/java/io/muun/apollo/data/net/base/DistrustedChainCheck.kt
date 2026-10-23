package io.muun.apollo.data.net.base

import android.net.http.X509TrustManagerExtensions
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Rejects TLS certificate chains that carry a [DistrustedCertificates] key.
 *
 * Checking only the certificates the server sent isn't enough: Android completes chains with
 * intermediates cached from earlier handshakes, so a compromised intermediate can be trusted
 * without being sent. We also rebuild the chain with the platform trust manager and check it too,
 * failing closed if it can't be rebuilt.
 */
class DistrustedChainCheck(
    private val verifiedChain: (served: List<Certificate>, host: String) -> List<Certificate>,
) {

    companion object {

        /**
         * Rebuild the chain with the platform trust manager, the same way OkHttp's
         * AndroidCertificateChainCleaner does.
         */
        fun withPlatformTrust(): DistrustedChainCheck {
            val trustManager = platformTrustManager()
            val extensions = X509TrustManagerExtensions(trustManager)

            return DistrustedChainCheck { served, host ->
                val certificates = served.map { it as X509Certificate }.toTypedArray()
                extensions.checkServerTrusted(certificates, "RSA", host)
            }
        }

        private fun platformTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }

    /**
     * Throw [SSLPeerUnverifiedException] if the chain carries a distrusted key or can't be rebuilt.
     */
    fun check(served: List<Certificate>, host: String) {
        val verified = try {
            verifiedChain(served, host)
        } catch (e: Exception) {
            throw SSLPeerUnverifiedException("Can't verify the server certificate chain")
                .apply { initCause(e) }
        }

        if (DistrustedCertificates.containsDistrustedKey(served + verified)) {
            throw SSLPeerUnverifiedException("Server certificate chain carries a distrusted key")
        }
    }
}
