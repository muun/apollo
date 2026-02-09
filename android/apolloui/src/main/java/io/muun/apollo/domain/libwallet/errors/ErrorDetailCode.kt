package io.muun.apollo.domain.libwallet.errors

enum class ErrorDetailCode(val code: Long) {
    SIGN_INTERNAL_ERROR(14_100),
    SIGN_MAC_VALIDATION_FAILED(14_101),
    CHALLENGE_EXPIRED(14_102),
    PAIR_INTERNAL_ERROR(14_103),
    NO_SLOTS_AVAILABLE(14_104),
    MUUN_APPLET_NOT_FOUND(14_105),
    UNKNOWN(14_999)
}


