package io.muun.apollo.data.afs

import java.util.Locale

class LocaleInfoProvider {

    val language: String
        get() {
            return Locale.getDefault().toString()
        }

    val regionCode: String
        get() {
            val locale = Locale.getDefault()
            return locale.country ?: Constants.EMPTY
        }
}