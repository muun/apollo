package io.muun.apollo.domain.model.marketplace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MarketplaceCountryCodeTest {

    @Test
    fun `constructing an assigned code should succeed`() {
        assertEquals("US", MarketplaceCountryCode("US").value)
    }

    @Test
    fun `constructing an unassigned or malformed code should throw`() {
        for (code in listOf("ZZ", "XX", "us", "USA", "")) {
            assertThrows(IllegalArgumentException::class.java) {
                MarketplaceCountryCode(code)
            }
        }
    }

    @Test
    fun `CODES should hold the full assigned set`() {
        assertEquals(249, MarketplaceCountryCode.CODES.size)
    }
}
