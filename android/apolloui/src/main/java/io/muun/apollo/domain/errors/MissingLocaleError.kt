package io.muun.apollo.domain.errors

import io.muun.common.exception.PotentialBug
import java.util.*

class MissingLocaleError(
    countryCode: String,
) : MuunError("No locales found for country:$countryCode"), PotentialBug {

    init {

        // Partitioning locales into several parts as they are many and crashlytics imposes a 1kb
        // limit for error metadata keys
        val availableLocales = Locale.getAvailableLocales().toList()
        val size = availableLocales.size

        for (i in 0..9) {
            val subList = availableLocales.subList(i * (size / 10), (i + 1) * size / 10)
            metadata["availableLocales$i"] = subList.toTypedArray().contentToString()
        }
    }
}