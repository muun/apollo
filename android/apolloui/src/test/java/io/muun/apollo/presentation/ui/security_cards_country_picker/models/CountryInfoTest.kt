package io.muun.apollo.presentation.ui.security_cards_country_picker.models

import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import org.junit.Assert.assertEquals
import org.junit.Test

class CountryInfoTest {

    @Test
    fun `the table should cover exactly MarketplaceCountryCode's codes`() {
        // This test is load-bearing: it proves the table covers every code
        // MarketplaceCountryCode can hold, which is what makes CountryInfo's
        // nameOf and flagOf safe to use getValue (no miss is possible).
        // Touching or weakening this test changes how CountryInfo behaves:
        // an uncovered code would throw at construction in production.
        val expected = MarketplaceCountryCode.CODES.sorted()
        val actual = CountryInfo.supportedCodes().map { it.value }.sorted()

        assertEquals(expected, actual)
    }
}
