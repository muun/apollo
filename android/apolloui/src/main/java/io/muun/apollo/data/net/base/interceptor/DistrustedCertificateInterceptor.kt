package io.muun.apollo.data.net.base.interceptor

import io.muun.apollo.data.net.base.DistrustedChainCheck
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Collections
import java.util.WeakHashMap

/**
 * Fails requests on connections whose certificate chain carries a distrusted key (see
 * [DistrustedChainCheck]), before anything is sent on them.
 *
 * Must be added as a network interceptor: application interceptors have no connection yet.
 */
class DistrustedCertificateInterceptor(
    private val chainCheck: DistrustedChainCheck,
) : Interceptor {

    companion object {

        fun withPlatformTrust(): DistrustedCertificateInterceptor {
            return DistrustedCertificateInterceptor(DistrustedChainCheck.withPlatformTrust())
        }
    }

    /**
     * Connections whose chain already passed the check. A connection's handshake never changes,
     * so later requests on it skip the chain rebuild. Weak keys let evicted connections go.
     */
    private val checkedConnections: MutableSet<Connection> =
        Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))

    override fun intercept(chain: Interceptor.Chain): Response {
        val connection = checkNotNull(chain.connection()) {
            "DistrustedCertificateInterceptor must be added as a network interceptor"
        }

        // Plain http (local builds) has no handshake to check.
        val handshake = connection.handshake() ?: return chain.proceed(chain.request())

        if (connection !in checkedConnections) {
            chainCheck.check(handshake.peerCertificates(), chain.request().url().host())
            checkedConnections.add(connection)
        }

        return chain.proceed(chain.request())
    }
}
