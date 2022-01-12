package io.muun.apollo.presentation.ui.helper

import android.annotation.SuppressLint
import android.text.TextUtils
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.helper.BitcoinHelper.formatInputBitcoinAmount
import io.muun.apollo.presentation.ui.helper.BitcoinHelper.formatLongBitcoinAmount
import io.muun.apollo.presentation.ui.helper.BitcoinHelper.formatShortBitcoinAmount
import io.muun.apollo.presentation.ui.view.RichText
import io.muun.common.model.Currency
import io.muun.common.utils.BitcoinUtils
import org.javamoney.moneta.Money
import org.javamoney.moneta.format.AmountFormatParams
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.MonetaryAmount
import javax.money.RoundingQueryBuilder
import javax.money.format.AmountFormatQueryBuilder
import javax.money.format.MonetaryFormats

object MoneyHelper {

    const val MAX_FRACTIONAL_DIGITS_FIAT = 2
    const val MAX_FRACTIONAL_DIGITS_BTC = 8

    private class FiatFormat(private val basePattern: String, private val decimalsPattern: Char) {

        fun buildFor(currency: CurrencyUnit): String =
            basePattern + getDefaultFractionDigits(currency, decimalsPattern)

        private fun getDefaultFractionDigits(currency: CurrencyUnit, symbol: Char): String {
            check(currency.defaultFractionDigits in 0..9) // But normally 0, 2 or 3 according to doc

            var digits = "."
            for (i in 0 until currency.defaultFractionDigits) {
                digits += symbol
            }
            return digits.removeSuffix(".")
        }
    }

    /**
     * Always display all decimals.
     */
    private val DEFAULT_FIAT_FORMAT: FiatFormat = FiatFormat("###,##0", '0')

    /**
     * "Flexible" display: no decimals required, but up to {@code currency.defaultFractionDigits}
     * decimals if precision requires it. Useful for handling of input/user edition.
     */
    private val INPUT_FIAT_FORMAT: FiatFormat = FiatFormat("###,###", '#')

    /**
     * Format a MonetaryAmount as RichText, to display in a TextView, allowing for different colors
     * for the amount and currency, and showing more decimals for BTC.
     */
    @JvmStatic
    fun toLongRichText(
        amt: MonetaryAmount,
        amtColor: Int,
        currencyColor: Int,
        bitcoinUnit: BitcoinUnit,
        locale: Locale
    ): CharSequence {

        val formattedAmount = formatLongMonetaryAmount(amt, false, bitcoinUnit, locale)
        val formatCurrency = formatCurrency(amt.currency, bitcoinUnit)

        return concatRichText(
            RichText(formattedAmount).setForegroundColor(amtColor),
            RichText(formatCurrency).setForegroundColor(currencyColor)
        )
    }

    private fun concatRichText(richText1: CharSequence, richText2: CharSequence): CharSequence =
        TextUtils.concat(richText1, " ", richText2)

    /**
     * Format a MonetaryAmount for the purposes of user input edition.
     */
    @JvmStatic
    fun formatInputMonetaryAmount(
        amount: MonetaryAmount,
        bitcoinUnit: BitcoinUnit,
        locale: Locale
    ): String {

        return if (amount.isBtc()) {
            formatInputBitcoinAmount(BitcoinUtils.bitcoinsToSatoshis(amount), bitcoinUnit, locale)

        } else {
            format(amount, INPUT_FIAT_FORMAT.buildFor(amount.currency), false, locale)
        }
    }

    /**
     * Format a monetary amount with high-precision decimals, forcing the display of the currency
     * code.
     */
    @JvmStatic
    fun formatLongMonetaryAmount(
        amount: MonetaryAmount,
        bitcoinUnit: BitcoinUnit,
        locale: Locale
    ): String {
        return formatLongMonetaryAmount(amount, true, bitcoinUnit, locale)
    }

    /**
     * Format a monetary amount with high-precision decimals.
     */
    @JvmStatic
    fun formatLongMonetaryAmount(
        amount: MonetaryAmount,
        showCurrencyCode: Boolean,
        bitcoinUnit: BitcoinUnit,
        locale: Locale
    ): String {

        return if (amount.isBtc()) {
            val amountInSats = BitcoinUtils.bitcoinsToSatoshis(amount)
            formatLongBitcoinAmount(amountInSats, showCurrencyCode, bitcoinUnit, locale)

        } else {
            format(amount, DEFAULT_FIAT_FORMAT.buildFor(amount.currency), showCurrencyCode, locale)
        }
    }

    /**
     * Format a monetary amount with low-precision decimals, forcing the display of the currency
     * code.
     */
    fun formatShortMonetaryAmount(
        amt: MonetaryAmount,
        bitcoinUnit: BitcoinUnit,
        locale: Locale
    ): String {
        return formatShortMonetaryAmount(amt, true, bitcoinUnit, locale)
    }

    /**
     * Format a monetary amount with low-precision decimals.
     */
    fun formatShortMonetaryAmount(
        amount: MonetaryAmount,
        showCurrencyCode: Boolean,
        bitcoinUnit: BitcoinUnit,
        locale: Locale
    ): String {

        return if (amount.isBtc()) {
            formatShortBitcoinAmount(
                BitcoinUtils.bitcoinsToSatoshis(amount),
                showCurrencyCode,
                bitcoinUnit,
                locale
            )
        } else {
            format(amount, DEFAULT_FIAT_FORMAT.buildFor(amount.currency), showCurrencyCode, locale)
        }
    }

    /**
     * Return a UI-ready representation of the currency.
     */
    @JvmStatic
    fun formatCurrency(currency: CurrencyUnit, bitcoinUnit: BitcoinUnit): String {
        return formatCurrency(currency.currencyCode, bitcoinUnit)
    }

    /**
     * Return a UI-ready representation of the currency code.
     */
    @SuppressLint("DefaultLocale")
    @JvmStatic
    fun formatCurrency(currencyCode: String, bitcoinUnit: BitcoinUnit): String {
        return if (currencyCode == "BTC" && bitcoinUnit == BitcoinUnit.SATS) {
            "SAT"
        } else {
            currencyCode.toUpperCase()
        }
    }

    /**
     * Return a UI-ready representation of the currency name, given the code.
     */
    @JvmStatic
    fun formatCurrencyName(currency: Currency, bitcoinUnit: BitcoinUnit): String {
        return if (currency.code == "BTC" && bitcoinUnit == BitcoinUnit.SATS) {
            "Satoshi"
        } else {
            currency.name
        }
    }

    /**
     * Round a monetary amount, keeping the appropriate number of digits for the currency.
     */
    @JvmStatic
    fun round(amount: MonetaryAmount): MonetaryAmount {
        val roundedNumber = amount.number
            .numberValue(BigDecimal::class.java)
            .setScale(amount.currency.maxFractionalDigits(), RoundingMode.UP)

        return Money.of(roundedNumber, amount.currency)
    }

    private fun format(
        fiatAmount: MonetaryAmount,
        pattern: String,
        showSymbol: Boolean,
        locale: Locale
    ): String {

        // left here as documentation
        // final String symbol = Currency.CURRENCIES.get(code).getSymbol();

        val fullPattern = pattern + if (showSymbol) " ¤" else ""
        val formatter = MonetaryFormats.getAmountFormat(
            AmountFormatQueryBuilder.of(locale)
                .set(AmountFormatParams.PATTERN, fullPattern)
                .build()
        )

        val rounding = Monetary.getRounding(
            RoundingQueryBuilder.of()
                .setScale(fiatAmount.currency.defaultFractionDigits)
                .set(RoundingMode.HALF_UP)
                .build()
        )

        return formatter.format(fiatAmount.with(rounding))
    }

    /**
     * Return the amount of fractional digits to use for a given currency.
     */
    private fun CurrencyUnit.maxFractionalDigits() =
        if (currencyCode == "BTC") MAX_FRACTIONAL_DIGITS_BTC else MAX_FRACTIONAL_DIGITS_FIAT
}