package io.muun.apollo.domain.action

import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.TelephonyInfoProvider
import io.muun.common.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.util.*

class CurrencyActionsTest : BaseTest() {

    companion object {
        private val LOCALE_NO_CURRENCY = Locale.ENGLISH
        private val LOCALE_ARGENTINA = Locale("es", "AR")
    }

    @Mock
    private lateinit var telephonyInfoProvider: TelephonyInfoProvider

    @InjectMocks
    private lateinit var currencyActions: CurrencyActions

    @Test
    fun getCurrencyForLocale() {
        var currency = currencyActions.getCurrencyForLocale(LOCALE_NO_CURRENCY)
        assertThat(currency.isPresent).isFalse

        currency = currencyActions.getCurrencyForLocale(Locale.US)
        assertThat(currency.isPresent).isTrue
        assertThat(currency.get().currencyCode).isEqualTo("USD")
    }

    @Test
    fun getCurrenciesForCountryCode() {
        val currencies = currencyActions.getCurrenciesForCountryCode("US")
        assertThat(currencies.isEmpty()).isFalse
    }

    @Test()
    fun `getCurrenciesForCountryCode unknown country`() {
        val currencies = currencyActions.getCurrenciesForCountryCode("XXX")
        assertThat(currencies.size).isEqualTo(1)
        assertThat(currencies.first().currencyCode).isEqualTo("USD")
    }

    @Test
    fun `getLocalCurrencies in US`() {
        `when`(telephonyInfoProvider.region).thenReturn(Optional.of("US"))

        val currencies = currencyActions.localCurrencies
        assertThat(currencies.size).isEqualTo(1)
        assertThat(currencies.first().currencyCode).isEqualTo("USD")
    }

    @Test
    fun `getLocalCurrencies in Argentina`() {
        `when`(telephonyInfoProvider.region).thenReturn(Optional.of("AR"))

        val currencies = currencyActions.localCurrencies
        assertThat(currencies.size).isEqualTo(1)
        assertThat(currencies.first().currencyCode).isEqualTo("ARS")
    }

    @Test
    fun `getLocalCurrencies locale default`() {
        `when`(telephonyInfoProvider.region).thenReturn(Optional.empty())

        val originalLocale = Locale.getDefault()

        try {
            Locale.setDefault(LOCALE_ARGENTINA)

            val currencies = currencyActions.localCurrencies
            assertThat(currencies.size).isEqualTo(1)
            assertThat(currencies.first().currencyCode).isEqualTo("ARS")

        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `getLocalCurrencies default to USD`() {
        `when`(telephonyInfoProvider.region).thenReturn(Optional.empty())

        val originalLocale = Locale.getDefault()

        try {
            Locale.setDefault(LOCALE_NO_CURRENCY)

            val currencies = currencyActions.localCurrencies
            assertThat(currencies.size).isEqualTo(1)
            assertThat(currencies.first().currencyCode).isEqualTo("USD")

        } finally {
            Locale.setDefault(originalLocale)
        }
    }

}
