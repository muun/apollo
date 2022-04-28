package io.muun.apollo.presentation.ui.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReceiveLnInvoiceFormatterTest {

    private val formatter = ReceiveLnInvoiceFormatter()

    @Test
    fun `formats duration over 24hs`() {
        val timeInSeconds = 30 * 3600 + 60 * 43 + 24L

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("30:43:24")
    }

    @Test
    fun `formats duration with exact hours`() {
        val timeInSeconds = 24 * 3600L

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("24:00:00")
    }

    @Test
    fun `formats duration under 24hs`() {
        val timeInSeconds = 19 * 3600 + 60 * 43 + 24L

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("19:43:24")
    }

    @Test
    fun `formats duration under 10hs`() {
        val timeInSeconds = 9 * 3600 + 60 * 43 + 24L

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("9:43:24")
    }

    @Test
    fun `formats duration under 1h`() {
        val timeInSeconds = 60 * 43 + 24L

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("0:43:24")
    }

    @Test
    fun `formats duration under 10 min`() {
        val timeInSeconds = 60 * 7 + 24L

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("0:07:24")
    }
}