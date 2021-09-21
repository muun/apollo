package io.muun.apollo.presentation.ui

import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.ui.helper.BitcoinHelper
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import org.assertj.core.api.Assertions.assertThat
import org.javamoney.moneta.Money
import org.junit.Test
import java.util.*
import javax.money.MonetaryAmount

class AmountFormattingTest {

    companion object {
        private val BTC = CurrencyDisplayMode.BTC
        private val SAT = CurrencyDisplayMode.SATS
    }

    private val locale = Locale.US

    @Test
    fun `long BTC format always displays 8 decimals`() {
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

        // Try amounts with grouping separators:
        checkLong(100_000_000_000, "1,000.00000000")
        checkLong(1_000_000_000_000, "10,000.00000000")
        checkLong(10_000_000_000_000, "100,000.00000000")
        checkLong(100_000_000_000_000, "1,000,000.00000000")
        checkLong(1_000_000_000_000_000, "10,000,000.00000000")

        // Try amounts with grouping separators and decimals:
        checkLong(100_010_000_000, "1,000.10000000")
        checkLong(1_000_000_000_001, "10,000.00000001")
        checkLong(10_000_010_000_001, "100,000.10000001")
        checkLong(100_000_012_345_678, "1,000,000.12345678")
    }

    @Test
    fun `short BTC format always displays 2 decimals, plus maybe 6`() {
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

        // Try amounts with grouping separators:
        checkShort(100_000_000_000, "1,000.00")
        checkShort(1_000_000_000_000, "10,000.00")
        checkShort(10_000_000_000_000, "100,000.00")
        checkShort(100_000_000_000_000, "1,000,000.00")
        checkShort(1_000_000_000_000_000, "10,000,000.00")

        // Try amounts with grouping separators and decimals:
        checkShort(100_010_000_000, "1,000.10")
        checkShort(1_000_000_000_001, "10,000.00000001")
        checkShort(10_000_010_000_001, "100,000.10000001")
        checkShort(100_000_012_345_678, "1,000,000.12345678")
    }

    @Test
    fun `input BTC format is FLEX (aka displays no decimals, but up to 8 if needed)`() {
        // Trailing zero trimming:
        checkInputBtc(0.0, "0")
        checkInputBtc(0.01, "0.01")
        checkInputBtc(0.12, "0.12")
        checkInputBtc(0.10, "0.1")
        checkInputBtc(0.90, "0.9")
        checkInputBtc(0.91, "0.91")

        // Rounding?: NO!
        checkInputBtc(12.344, "12.344")
        checkInputBtc(12.345, "12.345")
        checkInputBtc(12.3456, "12.3456")
        checkInputBtc(12.34567, "12.34567")
        checkInputBtc(12.345678, "12.345678")
        checkInputBtc(12.3456789, "12.3456789")
        checkInputBtc(12.34567891, "12.34567891")

        // Try amounts with grouping separators:
        checkInputBtc(1_000.0, "1,000")
        checkInputBtc(10_000.1, "10,000.1")
        checkInputBtc(100_000.01, "100,000.01")
        checkInputBtc(1_000_000.12, "1,000,000.12")
        checkInputBtc(10_000_000.10, "10,000,000.1")
        checkInputBtc(100_000_000.90, "100,000,000.9")
        checkInputBtc(1_000_000_000.91, "1,000,000,000.91")
    }

    @Test
    fun `amount in SATS are correctly formatted`() {

        // Check "LONG BTC format" for sats

        checkLong(1, "1", SAT)
        checkLong(12, "12", SAT)
        checkLong(123, "123", SAT)
        checkLong(1234, "1,234", SAT)
        checkLong(12345, "12,345", SAT)
        checkLong(123456, "123,456", SAT)
        checkLong(1234567, "1,234,567", SAT)
        checkLong(12345678, "12,345,678", SAT)
        checkLong(123456789, "123,456,789", SAT)
        checkLong(1234567891, "1,234,567,891", SAT)

        checkLong(0, "0", SAT)
        checkLong(100000000, "100,000,000", SAT)
        checkLong(1000000000, "1,000,000,000", SAT)

        checkLong(100_000_000_000, "100,000,000,000", SAT)
        checkLong(1_000_000_000_000, "1,000,000,000,000", SAT)
        checkLong(10_000_000_000_000, "10,000,000,000,000", SAT)
        checkLong(100_000_000_000_000, "100,000,000,000,000", SAT)
        checkLong(1_000_000_000_000_000, "1,000,000,000,000,000", SAT)

        checkLong(100_010_000_000, "100,010,000,000", SAT)
        checkLong(1_000_000_000_001, "1,000,000,000,001", SAT)
        checkLong(10_000_010_000_001, "10,000,010,000,001", SAT)
        checkLong(100_000_012_345_678, "100,000,012,345,678", SAT)

        // Check "SHORT BTC format" for sats

        checkShort(1, "1", SAT)
        checkShort(12, "12", SAT)
        checkShort(123, "123", SAT)
        checkShort(1234, "1,234", SAT)
        checkShort(12345, "12,345", SAT)
        checkShort(123456, "123,456", SAT)
        checkShort(1234567, "1,234,567", SAT)
        checkShort(12345678, "12,345,678", SAT)
        checkShort(123456789, "123,456,789", SAT)
        checkShort(1234567891, "1,234,567,891", SAT)

        checkShort(0, "0", SAT)
        checkShort(100000000, "100,000,000", SAT)
        checkShort(1000000000, "1,000,000,000", SAT)

        checkShort(100_000_000_000, "100,000,000,000", SAT)
        checkShort(1_000_000_000_000, "1,000,000,000,000", SAT)
        checkShort(10_000_000_000_000, "10,000,000,000,000", SAT)
        checkShort(100_000_000_000_000, "100,000,000,000,000", SAT)
        checkShort(1_000_000_000_000_000, "1,000,000,000,000,000", SAT)

        checkShort(100_010_000_000, "100,010,000,000", SAT)
        checkShort(1_000_000_000_001, "1,000,000,000,001", SAT)
        checkShort(10_000_010_000_001, "10,000,010,000,001", SAT)
        checkShort(100_000_012_345_678, "100,000,012,345,678", SAT)

        // Check "INPUT BTC format" for sats

        checkInputBtc(0.0, "0", SAT)
        checkInputBtc(0.01, "1,000,000", SAT)
        checkInputBtc(0.12, "12,000,000", SAT)
        checkInputBtc(0.10, "10,000,000", SAT)
        checkInputBtc(0.90, "90,000,000", SAT)
        checkInputBtc(0.91, "91,000,000", SAT)

        // Rounding?: NO!
        checkInputBtc(12.344, "1,234,400,000", SAT)
        checkInputBtc(12.345, "1,234,500,000", SAT)
        checkInputBtc(12.3456, "1,234,560,000", SAT)
        checkInputBtc(12.34567, "1,234,567,000", SAT)
        checkInputBtc(12.345678, "1,234,567,800", SAT)
        checkInputBtc(12.3456789, "1,234,567,890", SAT)
        checkInputBtc(12.34567891, "1,234,567,891", SAT)

        // Try amounts with grouping separators:
        checkInputBtc(1_000.0, "100,000,000,000", SAT)
        checkInputBtc(10_000.1, "1,000,010,000,000", SAT)
        checkInputBtc(100_000.01, "10,000,001,000,000", SAT)
        checkInputBtc(1_000_000.12, "100,000,012,000,000", SAT)
        checkInputBtc(10_000_000.10, "1,000,000,010,000,000", SAT)
        checkInputBtc(100_000_000.90, "10,000,000,090,000,000", SAT)
        checkInputBtc(1_000_000_000.91, "100,000,000,091,000,000", SAT)
    }

    @Test
    fun `long FIAT format rounds but DOES NOT remove trailing zeroes`() {
        var currency = "USD"

        // Trailing zero trimming:
        checkLongFiat(Money.of(0.0, currency), "0.00")
        checkLongFiat(Money.of(0.01, currency), "0.01")
        checkLongFiat(Money.of(0.12, currency), "0.12")
        checkLongFiat(Money.of(0.10, currency), "0.10")
        checkLongFiat(Money.of(0.90, currency), "0.90")
        checkLongFiat(Money.of(0.91, currency), "0.91")

        // Rounding:
        checkLongFiat(Money.of(12.344, currency), "12.34")
        checkLongFiat(Money.of(12.345, currency), "12.35")
        checkLongFiat(Money.of(12.346, currency), "12.35")

        // Try amounts with grouping separators:
        checkLongFiat(Money.of(1_000.0, currency), "1,000.00")
        checkLongFiat(Money.of(10_000.1, currency), "10,000.10")
        checkLongFiat(Money.of(100_000.01, currency), "100,000.01")
        checkLongFiat(Money.of(1_000_000.12, currency), "1,000,000.12")
        checkLongFiat(Money.of(10_000_000.10, currency), "10,000,000.10")
        checkLongFiat(Money.of(100_000_000.90, currency), "100,000,000.90")
        checkLongFiat(Money.of(1_000_000_000.91, currency), "1,000,000,000.91")

        // Now for Japanese Yen (no decimals)
        currency = "JPY"

        // Trailing zero trimming:
        checkLongFiat(Money.of(0.0, currency), "0")
        checkLongFiat(Money.of(0.01, currency), "0")
        checkLongFiat(Money.of(0.12, currency), "0")
        checkLongFiat(Money.of(0.10, currency), "0")
        checkLongFiat(Money.of(0.90, currency), "1")
        checkLongFiat(Money.of(0.91, currency), "1")

        // Rounding:
        checkLongFiat(Money.of(12.344, currency), "12")
        checkLongFiat(Money.of(12.345, currency), "12")
        checkLongFiat(Money.of(12.346, currency), "12")

        // Try amounts with grouping separators:
        checkLongFiat(Money.of(1_000.0, currency), "1,000")
        checkLongFiat(Money.of(10_000.1, currency), "10,000")
        checkLongFiat(Money.of(100_000.01, currency), "100,000")
        checkLongFiat(Money.of(1_000_000.12, currency), "1,000,000")
        checkLongFiat(Money.of(10_000_000.10, currency), "10,000,000")
        checkLongFiat(Money.of(100_000_000.90, currency), "100,000,001")
        checkLongFiat(Money.of(1_000_000_000.91, currency), "1,000,000,001")

        // Now with the Bahraini Dinar (3 decimals)
        currency = "BHD"

        // Trailing zero trimming:
        checkLongFiat(Money.of(0.0, currency), "0.000")
        checkLongFiat(Money.of(0.01, currency), "0.010")
        checkLongFiat(Money.of(0.12, currency), "0.120")
        checkLongFiat(Money.of(0.10, currency), "0.100")
        checkLongFiat(Money.of(0.90, currency), "0.900")
        checkLongFiat(Money.of(0.91, currency), "0.910")

        // Rounding:
        checkLongFiat(Money.of(12.344, currency), "12.344")
        checkLongFiat(Money.of(12.345, currency), "12.345")
        checkLongFiat(Money.of(12.346, currency), "12.346")
        checkLongFiat(Money.of(12.3444, currency), "12.344")
        checkLongFiat(Money.of(12.3455, currency), "12.346")
        checkLongFiat(Money.of(12.3466, currency), "12.347")

        // Try amounts with grouping separators:
        checkLongFiat(Money.of(1_000.0, currency), "1,000.000")
        checkLongFiat(Money.of(10_000.1, currency), "10,000.100")
        checkLongFiat(Money.of(100_000.01, currency), "100,000.010")
        checkLongFiat(Money.of(1_000_000.12, currency), "1,000,000.120")
        checkLongFiat(Money.of(10_000_000.10, currency), "10,000,000.100")
        checkLongFiat(Money.of(100_000_000.90, currency), "100,000,000.900")
        checkLongFiat(Money.of(1_000_000_000.91, currency), "1,000,000,000.910")
    }

    @Test
    fun `short FIAT format rounds but DOES NOT remove trailing zeroes`() {
        var currency = "USD"

        // Trailing zero trimming:
        checkShortFiat(Money.of(0.0, currency), "0.00")
        checkShortFiat(Money.of(0.01, currency), "0.01")
        checkShortFiat(Money.of(0.12, currency), "0.12")
        checkShortFiat(Money.of(0.10, currency), "0.10")
        checkShortFiat(Money.of(0.90, currency), "0.90")
        checkShortFiat(Money.of(0.91, currency), "0.91")

        // Rounding:
        checkShortFiat(Money.of(12.344, currency), "12.34")
        checkShortFiat(Money.of(12.345, currency), "12.35")
        checkShortFiat(Money.of(12.346, currency), "12.35")

        // Try amounts with grouping separators:
        checkShortFiat(Money.of(1_000.0, currency), "1,000.00")
        checkShortFiat(Money.of(10_000.1, currency), "10,000.10")
        checkShortFiat(Money.of(100_000.01, currency), "100,000.01")
        checkShortFiat(Money.of(1_000_000.12, currency), "1,000,000.12")
        checkShortFiat(Money.of(10_000_000.10, currency), "10,000,000.10")
        checkShortFiat(Money.of(100_000_000.90, currency), "100,000,000.90")
        checkShortFiat(Money.of(1_000_000_000.91, currency), "1,000,000,000.91")

        // Now for Japanese Yen (no decimals)
        currency = "JPY"

        // Trailing zero trimming:
        checkShortFiat(Money.of(0.0, currency), "0")
        checkShortFiat(Money.of(0.01, currency), "0")
        checkShortFiat(Money.of(0.12, currency), "0")
        checkShortFiat(Money.of(0.10, currency), "0")
        checkShortFiat(Money.of(0.90, currency), "1")
        checkShortFiat(Money.of(0.91, currency), "1")

        // Rounding:
        checkShortFiat(Money.of(12.344, currency), "12")
        checkShortFiat(Money.of(12.345, currency), "12")
        checkShortFiat(Money.of(12.346, currency), "12")

        // Try amounts with grouping separators:
        checkShortFiat(Money.of(1_000.0, currency), "1,000")
        checkShortFiat(Money.of(10_000.1, currency), "10,000")
        checkShortFiat(Money.of(100_000.01, currency), "100,000")
        checkShortFiat(Money.of(1_000_000.12, currency), "1,000,000")
        checkShortFiat(Money.of(10_000_000.10, currency), "10,000,000")
        checkShortFiat(Money.of(100_000_000.90, currency), "100,000,001")
        checkShortFiat(Money.of(1_000_000_000.91, currency), "1,000,000,001")

        // Now with the Bahraini Dinar (3 decimals)
        currency = "BHD"

        // Trailing zero trimming:
        checkShortFiat(Money.of(0.0, currency), "0.000")
        checkShortFiat(Money.of(0.01, currency), "0.010")
        checkShortFiat(Money.of(0.12, currency), "0.120")
        checkShortFiat(Money.of(0.10, currency), "0.100")
        checkShortFiat(Money.of(0.90, currency), "0.900")
        checkShortFiat(Money.of(0.91, currency), "0.910")

        // Rounding:
        checkShortFiat(Money.of(12.344, currency), "12.344")
        checkShortFiat(Money.of(12.345, currency), "12.345")
        checkShortFiat(Money.of(12.346, currency), "12.346")
        checkShortFiat(Money.of(12.3444, currency), "12.344")
        checkShortFiat(Money.of(12.3455, currency), "12.346")
        checkShortFiat(Money.of(12.3466, currency), "12.347")

        // Try amounts with grouping separators:
        checkShortFiat(Money.of(1_000.0, currency), "1,000.000")
        checkShortFiat(Money.of(10_000.1, currency), "10,000.100")
        checkShortFiat(Money.of(100_000.01, currency), "100,000.010")
        checkShortFiat(Money.of(1_000_000.12, currency), "1,000,000.120")
        checkShortFiat(Money.of(10_000_000.10, currency), "10,000,000.100")
        checkShortFiat(Money.of(100_000_000.90, currency), "100,000,000.900")
        checkShortFiat(Money.of(1_000_000_000.91, currency), "1,000,000,000.910")
    }

    @Test
    fun `input FIAT format rounds and removes trailing zeroes`() {
        var currency = "USD"

        // Trailing zero trimming:
        checkInputFiat(Money.of(0.0, currency), "0")
        checkInputFiat(Money.of(0.01, currency), "0.01")
        checkInputFiat(Money.of(0.12, currency), "0.12")
        checkInputFiat(Money.of(0.10, currency), "0.1")
        checkInputFiat(Money.of(0.90, currency), "0.9")
        checkInputFiat(Money.of(0.91, currency), "0.91")

        // Rounding:
        checkInputFiat(Money.of(12.344, currency), "12.34")
        checkInputFiat(Money.of(12.345, currency), "12.35")
        checkInputFiat(Money.of(12.346, currency), "12.35")

        // Try amounts with grouping separators:
        checkInputFiat(Money.of(1_000.0, currency), "1,000")
        checkInputFiat(Money.of(10_000.1, currency), "10,000.1")
        checkInputFiat(Money.of(100_000.01, currency), "100,000.01")
        checkInputFiat(Money.of(1_000_000.12, currency), "1,000,000.12")
        checkInputFiat(Money.of(10_000_000.10, currency), "10,000,000.1")
        checkInputFiat(Money.of(100_000_000.90, currency), "100,000,000.9")
        checkInputFiat(Money.of(1_000_000_000.91, currency), "1,000,000,000.91")

        // Now for Japanese Yen (no decimals)
        currency = "JPY"

        // Trailing zero trimming:
        checkInputFiat(Money.of(0.0, currency), "0")
        checkInputFiat(Money.of(0.01, currency), "0")
        checkInputFiat(Money.of(0.12, currency), "0")
        checkInputFiat(Money.of(0.10, currency), "0")
        checkInputFiat(Money.of(0.90, currency), "1")
        checkInputFiat(Money.of(0.91, currency), "1")

        // Rounding:
        checkInputFiat(Money.of(12.344, currency), "12")
        checkInputFiat(Money.of(12.345, currency), "12")
        checkInputFiat(Money.of(12.346, currency), "12")

        // Try amounts with grouping separators:
        checkInputFiat(Money.of(1_000.0, currency), "1,000")
        checkInputFiat(Money.of(10_000.1, currency), "10,000")
        checkInputFiat(Money.of(100_000.01, currency), "100,000")
        checkInputFiat(Money.of(1_000_000.12, currency), "1,000,000")
        checkInputFiat(Money.of(10_000_000.10, currency), "10,000,000")
        checkInputFiat(Money.of(100_000_000.90, currency), "100,000,001")
        checkInputFiat(Money.of(1_000_000_000.91, currency), "1,000,000,001")

        // Now for Japanese Yen (no decimals)
        currency = "JPY"

        // Trailing zero trimming:
        checkInputFiat(Money.of(0.0, currency), "0")
        checkInputFiat(Money.of(0.01, currency), "0")
        checkInputFiat(Money.of(0.12, currency), "0")
        checkInputFiat(Money.of(0.10, currency), "0")
        checkInputFiat(Money.of(0.90, currency), "1")
        checkInputFiat(Money.of(0.91, currency), "1")

        // Rounding:
        checkInputFiat(Money.of(12.344, currency), "12")
        checkInputFiat(Money.of(12.345, currency), "12")
        checkInputFiat(Money.of(12.346, currency), "12")

        // Try amounts with grouping separators:
        checkInputFiat(Money.of(1_000.0, currency), "1,000")
        checkInputFiat(Money.of(10_000.1, currency), "10,000")
        checkInputFiat(Money.of(100_000.01, currency), "100,000")
        checkInputFiat(Money.of(1_000_000.12, currency), "1,000,000")
        checkInputFiat(Money.of(10_000_000.10, currency), "10,000,000")
        checkInputFiat(Money.of(100_000_000.90, currency), "100,000,001")
        checkInputFiat(Money.of(1_000_000_000.91, currency), "1,000,000,001")

        // Now with the Bahraini Dinar (3 decimals)
        currency = "BHD"

        // Trailing zero trimming:
        checkInputFiat(Money.of(0.0, currency), "0")
        checkInputFiat(Money.of(0.01, currency), "0.01")
        checkInputFiat(Money.of(0.12, currency), "0.12")
        checkInputFiat(Money.of(0.10, currency), "0.1")
        checkInputFiat(Money.of(0.90, currency), "0.9")
        checkInputFiat(Money.of(0.91, currency), "0.91")

        // Rounding:
        checkInputFiat(Money.of(12.344, currency), "12.344")
        checkInputFiat(Money.of(12.345, currency), "12.345")
        checkInputFiat(Money.of(12.346, currency), "12.346")
        checkInputFiat(Money.of(12.3444, currency), "12.344")
        checkInputFiat(Money.of(12.3455, currency), "12.346")
        checkInputFiat(Money.of(12.3466, currency), "12.347")

        // Try amounts with grouping separators:
        checkInputFiat(Money.of(1_000.0, currency), "1,000")
        checkInputFiat(Money.of(10_000.1, currency), "10,000.1")
        checkInputFiat(Money.of(100_000.01, currency), "100,000.01")
        checkInputFiat(Money.of(1_000_000.12, currency), "1,000,000.12")
        checkInputFiat(Money.of(10_000_000.10, currency), "10,000,000.1")
        checkInputFiat(Money.of(100_000_000.90, currency), "100,000,000.9")
        checkInputFiat(Money.of(1_000_000_000.91, currency), "1,000,000,000.91")
    }

    private fun checkLong(amountInSat: Long, expect: String, mode: CurrencyDisplayMode = BTC) {
        assertThat(BitcoinHelper.formatLongBitcoinAmount(amountInSat, false, mode, locale))
            .isEqualTo(expect)
    }

    private fun checkShort(amountInSat: Long, expect: String, mode: CurrencyDisplayMode = BTC) {
        assertThat(BitcoinHelper.formatShortBitcoinAmount(amountInSat, false, mode, locale))
            .isEqualTo(expect)
    }

    private fun checkInputBtc(amountInBtc: Double, expect: String, mode: CurrencyDisplayMode = BTC) {

        // NOTE: using Double here is a little flimsy
        val money = Money.of(amountInBtc, "BTC")
        assertThat(MoneyHelper.formatInputMonetaryAmount(money, mode, locale))
            .isEqualTo(expect)
    }

    private fun checkLongFiat(monetaryAmount: MonetaryAmount, expect: String) {
        assertThat(MoneyHelper.formatLongMonetaryAmount(monetaryAmount, false, BTC, locale))
            .isEqualTo(expect)
    }

    private fun checkShortFiat(monetaryAmount: MonetaryAmount, expect: String) {
        assertThat(MoneyHelper.formatShortMonetaryAmount(monetaryAmount, false, BTC, locale))
            .isEqualTo(expect)
    }

    private fun checkInputFiat(monetaryAmount: MonetaryAmount, expect: String) {
        assertThat(MoneyHelper.formatInputMonetaryAmount(monetaryAmount, BTC, locale))
            .isEqualTo(expect)
    }
}