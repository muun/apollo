package io.muun.apollo.presentation.ui.helper;

import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.model.Currency;
import io.muun.common.utils.BitcoinUtils;

import android.text.TextUtils;
import org.javamoney.moneta.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;

public final class MoneyHelper {

    public static final int MAX_FRACTIONAL_DIGITS_FIAT = 2;
    public static final int MAX_FRACTIONAL_DIGITS_BTC = 8;

    /**
     * Format a MonetaryAmount as RichText, to display in a TextView, allowing for different colors
     * for the amount and currency.
     */
    public static CharSequence toRichText(MonetaryAmount amount,
                                          int amountColor,
                                          int currencyColor,
                                          CurrencyDisplayMode mode) {

        return concatWithRichText(
                amountColor,
                formatShortMonetaryAmount(amount, false, mode),
                currencyColor,
                formatCurrency(amount.getCurrency(), mode)
        );
    }

    /**
     * Format a MonetaryAmount as RichText, to display in a TextView, allowing for different colors
     * for the amount and currency, and showing more decimals for BTC.
     */
    public static CharSequence toLongRichText(MonetaryAmount amount,
                                              int amountColor,
                                              int currencyColor,
                                              CurrencyDisplayMode mode) {

        return concatWithRichText(amountColor,
                formatLongMonetaryAmount(amount, false, mode),
                currencyColor,
                formatCurrency(amount.getCurrency(), mode)
        );
    }

    private static CharSequence concatWithRichText(int amountColor,
                                                   String amountText,
                                                   int currencyColor,
                                                   String currencyText) {
        return TextUtils.concat(
                new RichText(amountText).setForegroundColor(amountColor),
                " ",
                new RichText(currencyText).setForegroundColor(currencyColor)
        );
    }

    /**
     * Format a MonetaryAmount for the purposes of user input edition.
     */
    public static String formatInputMonetaryAmount(MonetaryAmount a, CurrencyDisplayMode mode) {
        if (isBtc(a)) {
            return BitcoinHelper.formatInputBitcoinAmount(BitcoinUtils.bitcoinsToSatoshis(a), mode);
        } else {
            return formatInputNonBitcoinAmount(a);
        }
    }

    public static String formatLongMonetaryAmount(MonetaryAmount amount, CurrencyDisplayMode mode) {
        return formatLongMonetaryAmount(amount, true, mode);
    }

    /**
     * Format a monetary amount with high-precision decimals.
     */
    public static String formatLongMonetaryAmount(MonetaryAmount amount,
                                                  boolean showCurrencyCode,
                                                  CurrencyDisplayMode mode) {

        if (isBtc(amount)) {
            return BitcoinHelper.formatLongBitcoinAmount(
                    BitcoinUtils.bitcoinsToSatoshis(amount),
                    showCurrencyCode,
                    mode
            );

        } else {
            return formatNonBitcoinAmount(amount, showCurrencyCode);
        }
    }

    public static String formatShortMonetaryAmount(MonetaryAmount amt, CurrencyDisplayMode mode) {
        return formatShortMonetaryAmount(amt, true, mode);
    }

    /**
     * Format a monetary amount with low-precision decimals.
     */
    public static String formatShortMonetaryAmount(MonetaryAmount amount,
                                                   boolean showCurrencyCode,
                                                   CurrencyDisplayMode mode) {
        if (isBtc(amount)) {
            return BitcoinHelper.formatShortBitcoinAmount(
                    BitcoinUtils.bitcoinsToSatoshis(amount),
                    showCurrencyCode,
                    mode
            );

        } else {
            return formatNonBitcoinAmount(amount, showCurrencyCode);
        }
    }

    public static String formatCurrency(CurrencyUnit currency, CurrencyDisplayMode mode) {
        return formatCurrency(currency.getCurrencyCode(), mode);
    }

    /**
     * Return a UI-ready representation of the currency code.
     */
    public static String formatCurrency(String currencyCode, CurrencyDisplayMode mode) {
        if (isBtc(currencyCode) && mode == CurrencyDisplayMode.SATS) {
            return "SAT";
        } else {
            return currencyCode.toUpperCase();
        }
    }

    /**
     * Return a UI-ready representation of the currency name, given the code.
     */
    public static String formatCurrencyName(Currency currency, CurrencyDisplayMode mode) {
        if (isBtc(currency.getCode()) && mode == CurrencyDisplayMode.SATS) {
            return "Satoshi";
        } else {
            return currency.getName();
        }
    }

    public static boolean isBtc(MonetaryAmount amount) {
        return isBtc(amount.getCurrency());
    }

    public static boolean isBtc(CurrencyUnit currency) {
        return currency.getCurrencyCode().equals("BTC");
    }

    private static boolean isBtc(String currencyCode) {
        return currencyCode.equals("BTC");
    }

    /**
     * Return the amount of fractional digits to use for a given currency.
     */
    public static int getMaxFractionalDigits(CurrencyUnit currency) {
        return currency.getCurrencyCode().equals("BTC")
                ? MAX_FRACTIONAL_DIGITS_BTC
                : MAX_FRACTIONAL_DIGITS_FIAT;
    }

    /**
     * Round a monetary amount, keeping the appropriate number of digits for the currency.
     */
    public static MonetaryAmount round(MonetaryAmount amount) {
        final int maxFractionalDigits = getMaxFractionalDigits(amount.getCurrency());

        final BigDecimal roundedNumber = amount.getNumber()
                .numberValue(BigDecimal.class)
                .setScale(maxFractionalDigits, RoundingMode.UP);

        return Money.of(roundedNumber, amount.getCurrency());
    }

    private static String formatNonBitcoinAmount(MonetaryAmount amount, boolean showCurrencyCode) {
        final String code = amount.getCurrency().getCurrencyCode();

        // left here as documentation
        // final String symbol = Currency.CURRENCIES.get(code).getSymbol();

        final String number = amount.getNumber()
                .numberValue(BigDecimal.class)
                .setScale(amount.getCurrency().getDefaultFractionDigits(), BigDecimal.ROUND_HALF_UP)
                .toPlainString();

        if (showCurrencyCode) {
            return number + " " + code;
        } else {
            return number;
        }
    }

    private static String formatInputNonBitcoinAmount(MonetaryAmount amount) {
        final String nonTrimmedString = amount.getNumber()
                .numberValue(BigDecimal.class)
                .setScale(amount.getCurrency().getDefaultFractionDigits(), BigDecimal.ROUND_HALF_UP)
                .toPlainString();

        // NOTE: in Java8, custom code is not necessary, since BigDecimal.stripTrailingZeroes
        // behaves as expected. Not in Java7: see https://stackoverflow.com/a/18917639/469300
        return nonTrimmedString.replaceAll("[.]?0*$", "");
    }

    private MoneyHelper() {
        throw new AssertionError();
    }
}
