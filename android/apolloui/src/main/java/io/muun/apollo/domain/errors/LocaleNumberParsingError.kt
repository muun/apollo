package io.muun.apollo.domain.errors

import java.util.*

class LocaleNumberParsingError(number: String, locale: Locale, cause: Throwable) : MuunError(
    cause
) {
    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["numberString"] = number
        metadata["locale"] = locale.toString()
    }
}