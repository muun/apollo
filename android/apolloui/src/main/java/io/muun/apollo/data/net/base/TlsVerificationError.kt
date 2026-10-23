package io.muun.apollo.data.net.base

import io.muun.apollo.domain.utils.isCausedByError
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * The server's certificate chain couldn't be trusted: unknown CA, hostname mismatch, certificate
 * pin mismatch, or an expired or not yet valid certificate (e.g. wrong device clock). Kept apart
 * from other [NetworkException]s because it isn't a connectivity issue and may mean someone is
 * intercepting the connection.
 */
class TlsVerificationError : NetworkException {

    companion object {

        /**
         * Whether the error comes from rejecting the server's certificate chain, as opposed to a
         * handshake that failed for transport reasons (e.g. connection reset mid-handshake).
         */
        @JvmStatic
        fun isTlsTrustFailure(error: Throwable): Boolean {
            return error is SSLPeerUnverifiedException
                || (error is SSLHandshakeException && error.isCausedByError<CertificateException>())
        }
    }

    constructor(cause: Throwable) : super(cause)

    constructor(url: String, cause: Throwable) : super(url, cause)
}
