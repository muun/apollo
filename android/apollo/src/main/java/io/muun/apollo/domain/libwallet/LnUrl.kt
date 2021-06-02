package io.muun.apollo.domain.libwallet

import libwallet.Libwallet
import timber.log.Timber

object LnUrl {

    fun isValid(text: String): Boolean {
        return try {
            Libwallet.lnurlValidate(text)
        } catch (e: Throwable) {
            Timber.e(e)
            false
        }
    }
}