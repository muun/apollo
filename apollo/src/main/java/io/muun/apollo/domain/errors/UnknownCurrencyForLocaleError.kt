package io.muun.apollo.domain.errors

import java.util.*

class UnknownCurrencyForLocaleError(locale: Locale, cause: Throwable):
    MuunError("Unsupported or unknown currency for locale: $locale", cause)
