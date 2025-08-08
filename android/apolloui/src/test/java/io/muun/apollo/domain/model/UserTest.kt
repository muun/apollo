package io.muun.apollo.domain.model

import io.muun.apollo.data.external.Gen
import io.muun.common.model.ExchangeRateProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import javax.money.Monetary

class UserTest {

    @Test
    fun testSalvadoranColonShouldFallbackToUsdUnderNormalCircumstances() {
        // This test checks our {@link io.muun.common.model.Currency } that Salvadoran Colon (SVC),
        // deprecated, gets defaulted to US Dollar (USD), under normal circumstances (e.g we DO
        // have an available rate for the defaulting currency, in this case USD).

        val currency = Monetary.getCurrency("SVC")!!
        val user = Gen.user(primaryCurrency = currency)
        val exchangeRateProvider = ExchangeRateProvider(
            mapOf("BTC" to 1.0, "ARS" to 10_000.0, "USD" to 1_000.0)
        )
        assertThat(user.getPrimaryCurrency(exchangeRateProvider).currencyCode).isEqualTo("USD")
    }

    @Test
    fun testCurrencyOverrideCurrencyNotExisting() {
        // This test checks that if we have an override/defaulting currency for an available
        // currency AND the defaulting currency IS not available, then we fallback to BTC.

        val currency = Monetary.getCurrency("SVC")!!
        val user = Gen.user(primaryCurrency = currency)
        val exchangeRateProvider = ExchangeRateProvider(
            mapOf("BTC" to 1.0, "ARS" to 10_000.0, "SVC" to 500.0)
        )
        assertThat(user.getPrimaryCurrency(exchangeRateProvider).currencyCode).isEqualTo("BTC")
    }

    @Test
    fun testCurrencyNotExistingAndNeitherDoesOverrideCurrency() {
        // This test checks that if we have an override/defaulting currency for a currency that's
        // not available, AND the defaulting currency IS ALSO not available, then we fallback to
        // BTC.

        val currency = Monetary.getCurrency("SVC")!!
        val user = Gen.user(primaryCurrency = currency)
        val exchangeRateProvider = ExchangeRateProvider(
            mapOf("BTC" to 1.0, "ARS" to 10_000.0)
        )
        assertThat(user.getPrimaryCurrency(exchangeRateProvider).currencyCode).isEqualTo("BTC")
    }

    @Test
    fun testCurrencyNotExistingAndTheresNoOverrideCurrency() {
        // This test checks that if we DO NOT have an override/defaulting currency for a currency
        // that's not available, then we fallback to BTC.

        val currency = Monetary.getCurrency("SSP")!!
        val user = Gen.user(primaryCurrency = currency)
        val exchangeRateProvider = ExchangeRateProvider(
            mapOf("BTC" to 1.0, "ARS" to 10_000.0, "SVC" to 500.0)
        )
        assertThat(user.getPrimaryCurrency(exchangeRateProvider).currencyCode).isEqualTo("BTC")
    }
}