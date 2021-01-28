package io.muun.apollo.presentation.ui

import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.ui.helper.BitcoinHelper
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import org.assertj.core.api.Assertions.assertThat
import org.javamoney.moneta.Money
import org.junit.Test

class AmountFormattingTest {

    @Test
    fun `long format always displays 8 decimals`() {
        // Try amounts that could be rounded:
        checkLong(1, "0.00000001")
        checkLong(12, "0.00000012")
        checkLong(123, "0.00000123")
        checkLong(1234, "0.00001234")
        checkLong(12345, "0.00012345")
        checkLong(123456, "0.00123456")
        checkLong(1234567, "0.01234567")
        checkLong(12345678, "0.12345678")
        checkLong(123456789, "1.23456789")
        checkLong(1234567891, "12.34567891")

        // Try amounts with trailing zeroes:
        checkLong(0, "0.00000000")
        checkLong(100000000, "1.00000000")
        checkLong(1000000000, "10.00000000")
    }

    @Test
    fun `short format always displays 2 decimals, plus maybe 6`() {
        // Try amounts that could be rounded:
        checkShort(1, "0.00000001")
        checkShort(12, "0.00000012")
        checkShort(123, "0.00000123")
        checkShort(1234, "0.00001234")
        checkShort(12345, "0.00012345")
        checkShort(123456, "0.00123456")
        checkShort(1234567, "0.01234567")
        checkShort(12345678, "0.12345678")
        checkShort(123456789, "1.23456789")
        checkShort(1234567891, "12.34567891")

        // Try amounts with trailing zeroes:
        checkShort(0, "0.00")
        checkShort(100000000, "1.00")
        checkShort(1000000000, "10.00")
    }

    @Test
    fun `input FIAT format rounds and removes trailing zeroes`() {
        // Trailing zero trimming:
        checkInputFiat(0.0, "0")
        checkInputFiat(0.01, "0.01")
        checkInputFiat(0.12, "0.12")
        checkInputFiat(0.10, "0.1")
        checkInputFiat(0.90, "0.9")
        checkInputFiat(0.91, "0.91")

        // Rounding:
        checkInputFiat(12.344, "12.34")
        checkInputFiat(12.345, "12.35")
        checkInputFiat(12.346, "12.35")
    }

    private fun checkShort(amount: Long,
                           expect: String,
                           showUnit: Boolean = false,
                           mode: CurrencyDisplayMode = CurrencyDisplayMode.BTC) {
        assertThat(BitcoinHelper.formatShortBitcoinAmount(amount, showUnit, mode)).isEqualTo(expect)
    }

    private fun checkLong(amount: Long,
                          expect: String,
                          showUnit: Boolean = false,
                          mode: CurrencyDisplayMode = CurrencyDisplayMode.BTC) {
        assertThat(BitcoinHelper.formatLongBitcoinAmount(amount, showUnit, mode)).isEqualTo(expect)
    }

    private fun checkInputFiat(amount: Double,
                               expect: String,
                               mode: CurrencyDisplayMode = CurrencyDisplayMode.BTC) {

        // NOTE: using Double here is a little flimsy
        val money = Money.of(amount, "USD")
        assertThat(MoneyHelper.formatInputMonetaryAmount(money, mode)).isEqualTo(expect)
    }
}