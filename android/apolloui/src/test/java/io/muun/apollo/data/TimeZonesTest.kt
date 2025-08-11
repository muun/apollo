package io.muun.apollo.data

import org.assertj.core.api.Assertions
import org.junit.Test
import org.threeten.bp.ZoneId

class TimeZonesTest {

    @Test
    fun testSupportLatestTzReleases() { // Regression test?

        // Test we support 2022b release of the tz code and data
        val kievZoneId = ZoneId.of("Europe/Kyiv", ZoneId.SHORT_IDS)

        // Let's assert anything/something basic
        Assertions.assertThat(kievZoneId.id).isEqualTo("Europe/Kyiv")

        // Test we support 2022g release of the tz code and data
        val ciudadJuarezZoneId = ZoneId.of("America/Ciudad_Juarez", ZoneId.SHORT_IDS)

        // Let's assert anything/something basic
        Assertions.assertThat(ciudadJuarezZoneId.id).isEqualTo("America/Ciudad_Juarez")
    }
}