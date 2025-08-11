package io.muun.apollo.domain.analytics

import org.junit.Test
import java.util.Locale

class AnalyticsEventTest {

    @Test
    fun usingLocaleDefaultCanGenerateDifferentLowercaseEncodings() {

        val examples = listOf("E_PIN", "S_SETTINGS")

        val enLocale = Locale.forLanguageTag("en_US")
        val trLocale = Locale.forLanguageTag("tr-TR")

        examples.forEach { example ->
            val lowerEn = example.lowercase(enLocale)
            val lowerTr = example.lowercase(trLocale)

            assert(lowerEn != lowerTr) {
                "$lowerEn shouldn't match $lowerTr. I turns to \"i\" and \"ı\" (dotless i, U+0131)"
            }
        }
    }

    @Test
    fun localeRootWorksAsIntended() {
        AnalyticsEvent::class.nestedClasses.forEach { analyticsEvent ->

            val lowercaseEn = analyticsEvent.simpleName?.lowercase(Locale.ENGLISH)
            val lowercaseRoot = analyticsEvent.simpleName?.lowercase(Locale.ROOT)

            assert(lowercaseEn == lowercaseRoot) {
                "$lowercaseEn didn't match $lowercaseRoot"
            }
        }
    }
}