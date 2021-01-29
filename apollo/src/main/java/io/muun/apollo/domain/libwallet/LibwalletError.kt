package io.muun.apollo.domain.libwallet

import libwallet.Libwallet
import io.muun.apollo.domain.errors.MuunError

class LibwalletError(val kind: Kind, message: String, cause: Throwable) :
        MuunError(message, cause) {

    enum class Kind(val code: Long) {
        UNKNOWN(1L),
        INVALID_URI(2L),
        NETWORK(3L),
        INVALID_PRIVATE_KEY(4L),
        INVALID_DERIVATION_PATH(5L);

        companion object {

            fun fromCode(code: Long): Kind? {
                for (v in values()) {
                    if (code == v.code) {
                        return v
                    }
                }
                return null
            }

        }
    }

    companion object {

        fun from(e: Exception): LibwalletError {
            val code = Libwallet.errorCode(e)
            val kind = Kind.fromCode(code)
            var message = e.message ?: ""
            if (kind == null) {
                message += " (code ${code})"
            }
            return LibwalletError(kind ?: Kind.UNKNOWN, message, e)
        }
    }

}