package io.muun.apollo.data.os

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class LocaleInfoProvider @Inject constructor() {

    val language: String
        get() {
            return Locale.getDefault().toString()
        }

    val dateFormat: String
        get() {
            val locale = Locale.getDefault()
            val dateFormat =
                DateFormat.getDateInstance(DateFormat.SHORT, locale) as SimpleDateFormat
            return dateFormat.toPattern()
        }

    val regionCode: String
        get() {
            val locale = Locale.getDefault()
            return locale.country ?: Constants.EMPTY
        }
}