package io.muun.apollo.presentation.ui.helper

import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.common.utils.BitcoinUtils
import org.javamoney.moneta.Money
import org.javamoney.moneta.format.AmountFormatParams
import java.math.RoundingMode
import java.util.*
import javax.money.Monetary
import javax.money.MonetaryAmount
import javax.money.RoundingQueryBuilder
import javax.money.format.AmountFormatQueryBuilder
import javax.money.format.MonetaryFormats

object BitcoinHelper {

    /**
     * Always display all 8 decimals.
     */
    private const val LONG_BITCOIN_FORMAT = "###,##0.00000000"

    /**
     * Display at least 2 decimals, then up to 8 if precision requires it.
     */
    private const val SHORT_BITCOIN_FORMAT = "###,##0.00######"

    /**
     * "Flexible" display: no decimals required, but up to 8 decimals if precision requires it.
     * Useful for handling of input/user edition.
     */
    private const val FLEX_BITCOIN_FORMAT = "###,##0.########"

    /**
     * Display an integer amount (sats). No decimals, no monkey business.
     */
    private const val SAT_FORMAT = "###,###"

    /**
     * Formats an amount of Bitcoins given in satoshis for the purposes of user edition.
     */
    @JvmStatic
    fun formatInputBitcoinAmount(
        amountInSats: Long,
        mode: CurrencyDisplayMode,
        locale: Locale
    ): String {
        return formatFlexBitcoinAmount(amountInSats, false, mode, locale)
    }

    /**
     * Formats an amount of Bitcoins given in satoshis to a flexible-precision Bitcoin amount.
     */
    fun formatFlexBitcoinAmount(
        amountInSats: Long,
        showCurrencyCode: Boolean,
        mode: CurrencyDisplayMode,
        locale: Locale
    ): String {

        return if (mode == CurrencyDisplayMode.SATS) {
            formatAmountInSat(amountInSats, showCurrencyCode, locale)

        } else {
            val amountInBtc = BitcoinUtils.satoshisToBitcoins(amountInSats)
            amountInBtc.format(FLEX_BITCOIN_FORMAT, showCurrencyCode, locale)
        }
    }

    /**
     * Formats an amount of Bitcoins given in satoshis to a high-precision Bitcoin amount, forcing
     * the display of the currency code.
     */
    @JvmStatic
    fun formatLongBitcoinAmount(amount: Long, mode: CurrencyDisplayMode, locale: Locale): String {
        return formatLongBitcoinAmount(amount, true, mode, locale)
    }

    /**
     * Formats an amount of Bitcoins given in satoshis to a high-precision Bitcoin amount.
     */
    @JvmStatic
    fun formatLongBitcoinAmount(
        amountInSats: Long,
        showCurrencyCode: Boolean,
        mode: CurrencyDisplayMode,
        locale: Locale
    ): String {

        return if (mode == CurrencyDisplayMode.SATS) {
            formatAmountInSat(amountInSats, showCurrencyCode, locale)

        } else {
            val amountInBtc = BitcoinUtils.satoshisToBitcoins(amountInSats)
            amountInBtc.format(LONG_BITCOIN_FORMAT, showCurrencyCode, locale)
        }
    }

    /**
     * Formats an amount of Bitcoins given in satoshis to a low-precision Bitcoin amount, forcing
     * the display of the currency code.
     */
    fun formatShortBitcoinAmount(
        amountInSats: Long,
        mode: CurrencyDisplayMode,
        locale: Locale
    ): String {
        return formatShortBitcoinAmount(amountInSats, true, mode, locale)
    }

    /**
     * Formats an amount of Bitcoins given in satoshis to a low-precision Bitcoin amount.
     */
    @JvmStatic
    fun formatShortBitcoinAmount(
        amountInSats: Long,
        showCurrencyCode: Boolean,
        mode: CurrencyDisplayMode,
        locale: Locale
    ): String {

        return if (mode == CurrencyDisplayMode.SATS) {
            formatAmountInSat(amountInSats, showCurrencyCode, locale)

        } else {
            val amountInBtc = BitcoinUtils.satoshisToBitcoins(amountInSats)
            amountInBtc.format(SHORT_BITCOIN_FORMAT, showCurrencyCode, locale)
        }
    }

    private fun MonetaryAmount.format(pattern: String, showCode: Boolean, locale: Locale): String {

        val fullPattern = pattern + if (showCode) " ¤" else ""

        val formatter = MonetaryFormats.getAmountFormat(
            AmountFormatQueryBuilder.of(locale)
                .set(AmountFormatParams.PATTERN, fullPattern)
                .build()
        )

        val rounding = Monetary.getRounding(
            RoundingQueryBuilder.of()
                .setScale(currency.defaultFractionDigits)
                .set(RoundingMode.HALF_UP)
                .build()
        )

        return formatter.format(this.with(rounding))
    }

    private fun formatAmountInSat(amountInSat: Long, showSymbol: Boolean, locale: Locale): String {
        val formatter = MonetaryFormats.getAmountFormat(
            AmountFormatQueryBuilder.of(locale)
                .set(AmountFormatParams.PATTERN, SAT_FORMAT)
                .build()
        )

        // Using BTC as currencyCode but its irrelevant since our pattern doesn't display it
        val formattedAmount = formatter.format(Money.of(amountInSat, "BTC"))
        return formattedAmount + if (showSymbol) " SAT" else ""
    }
}