package io.muun.apollo.presentation.ui.utils

import android.content.Context
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import io.muun.apollo.R
import io.muun.common.utils.Dates.DAY_IN_SECONDS
import io.muun.common.utils.Dates.HOUR_IN_SECONDS
import io.muun.common.utils.Dates.MINUTE_IN_SECONDS
import io.muun.common.utils.Dates.WEEK_IN_SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.Locale

class NewOperationInvoiceFormatterTest {

    private val captor0 = argumentCaptor<Long>()
    private val captor1 = argumentCaptor<Long>()
    private val captor2 = argumentCaptor<Long>()
    private val captor3 = argumentCaptor<Long>()
    private val captor4 = argumentCaptor<Long>()
    private val captor5 = argumentCaptor<Long>()
    private val captor6 = argumentCaptor<Long>()
    private val captor7 = argumentCaptor<Long>()
    private val captor8 = argumentCaptor<Long>()
    private val captor9 = argumentCaptor<Long>()

    private val formatter = NewOperationInvoiceFormatter(buildContext(Locale.US))
    private val formatterES = NewOperationInvoiceFormatter(buildContext(Locale("es", "AR")))

    private fun buildContext(locale: Locale): Context {

        val context = mock<Context>()

        doAnswer {
            returnWeeksText(locale, captor0.firstValue, captor1.firstValue)
        }.`when`(context)
            .getString(
                eq(R.string.new_operation_invoice_exp_weeks),
                captor0.capture(),
                captor1.capture()
            )

        doAnswer {
            returnDaysText(locale, captor2.firstValue, captor3.firstValue)
        }.`when`(context)
            .getString(
                eq(R.string.new_operation_invoice_exp_days),
                captor2.capture(),
                captor3.capture()
            )

        doAnswer {
            returnHoursText(locale, captor4.firstValue, captor5.firstValue)
        }.`when`(context)
            .getString(
                eq(R.string.new_operation_invoice_exp_hours),
                captor4.capture(),
                captor5.capture()
            )

        doAnswer {
            returnMinutesText(captor6.firstValue, captor7.firstValue)
        }.`when`(context)
            .getString(
                eq(R.string.new_operation_invoice_exp_minutes),
                captor6.capture(),
                captor7.capture()
            )

        doAnswer {
            returnSecondsText(captor8.firstValue, captor9.firstValue)
        }.`when`(context)
            .getString(
                eq(R.string.new_operation_invoice_exp_seconds),
                captor8.capture(),
                captor9.capture()
            )

        return context
    }

    private fun returnWeeksText(locale: Locale, weeks: Long, days: Long): String =
        if (locale == Locale.US) {
            "$weeks weeks and $days days"
        } else {
            "$weeks semanas y $days dias"
        }

    private fun returnDaysText(locale: Locale, days: Long, hours: Long): String =
        if (locale == Locale.US) {
            "$days days and $hours hours"
        } else {
            "$days dias y $hours horas"
        }

    private fun returnHoursText(locale: Locale, hours: Long, minutes: Long): String =
        if (locale == Locale.US) {
            "$hours hours and $minutes minutes"
        } else {
            "$hours horas y $minutes minutos"
        }

    private fun returnMinutesText(minutes: Long, seconds: Long): String =
        String.format("%02d:%02d", minutes, seconds)

    private fun returnSecondsText(minutes: Long, seconds: Long): String =
        String.format("%02d:%02d", minutes, seconds)

    @Test
    fun `test duration in weeks`() {

        val timeInSeconds = 1 * WEEK_IN_SECONDS +
                3 * DAY_IN_SECONDS +
                2 * HOUR_IN_SECONDS +
                40 * MINUTE_IN_SECONDS +
                24

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("1 weeks and 3 days")
        assertThat(formatterES.formatSeconds(timeInSeconds)).isEqualTo("1 semanas y 3 dias")
    }

    @Test
    fun `test duration in days`() {

        val timeInSeconds = 3 * DAY_IN_SECONDS +
                2 * HOUR_IN_SECONDS +
                40 * MINUTE_IN_SECONDS +
                24

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("3 days and 2 hours")
        assertThat(formatterES.formatSeconds(timeInSeconds)).isEqualTo("3 dias y 2 horas")
    }

    @Test
    fun `test duration in hours`() {

        val timeInSeconds = 2 * HOUR_IN_SECONDS +
                40 * MINUTE_IN_SECONDS +
                24

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("2 hours and 40 minutes")
        assertThat(formatterES.formatSeconds(timeInSeconds)).isEqualTo("2 horas y 40 minutos")
    }

    @Test
    fun `test duration in minutes`() {

        val timeInSeconds = 40 * MINUTE_IN_SECONDS + 24

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("40:24")
        assertThat(formatterES.formatSeconds(timeInSeconds)).isEqualTo("40:24")
    }

    @Test
    fun `test duration in seconds`() {

        val timeInSeconds = 24L

        assertThat(formatter.formatSeconds(timeInSeconds)).isEqualTo("00:24")
        assertThat(formatterES.formatSeconds(timeInSeconds)).isEqualTo("00:24")
    }
}