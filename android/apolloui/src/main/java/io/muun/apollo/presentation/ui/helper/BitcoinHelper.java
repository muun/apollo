package io.muun.apollo.presentation.ui.helper;

import io.muun.apollo.domain.model.CurrencyDisplayMode;

import androidx.annotation.NonNull;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import java.util.Locale;

public final class BitcoinHelper {

    private static final MonetaryFormat LONG_FORMAT = new MonetaryFormat()
            .shift(0)
            .minDecimals(8);

    private static final MonetaryFormat SHORT_FORMAT = MonetaryFormat.BTC
            .minDecimals(2)
            .repeatOptionalDecimals(1, 6);

    private static final MonetaryFormat INPUT_FORMAT = new MonetaryFormat()
            .minDecimals(0)
            .repeatOptionalDecimals(1, 8);


    public static String formatInputBitcoinAmount(long amount, CurrencyDisplayMode mode) {
        return formatFlexBitcoinAmount(amount, false, mode);
    }

    public static String formatFlexBitcoinAmount(long amount,
                                                 boolean showCurrencyCode,
                                                 CurrencyDisplayMode mode) {
        return format(INPUT_FORMAT, amount, showCurrencyCode, mode);
    }

    public static String formatLongBitcoinAmount(long amount, CurrencyDisplayMode mode) {
        return formatLongBitcoinAmount(amount, true, mode);
    }

    /**
     * Formats an amount of Bitcoins given in satoshis to a high-precision Bitcoin amount.
     */
    public static String formatLongBitcoinAmount(long amount,
                                                 boolean showCurrencyCode,
                                                 CurrencyDisplayMode mode) {

        return format(getFormatWithLocale(LONG_FORMAT), amount, showCurrencyCode, mode);
    }

    public static String formatShortBitcoinAmount(long amount, CurrencyDisplayMode mode) {
        return formatShortBitcoinAmount(amount, true, mode);
    }

    /**
     * Formats an amount of Bitcoins given in satoshis to a low-precision Bitcoin amount.
     */
    public static String formatShortBitcoinAmount(long amount,
                                                  boolean showCurrencyCode,
                                                  CurrencyDisplayMode mode) {

        return format(getFormatWithLocale(SHORT_FORMAT), amount, showCurrencyCode, mode);
    }

    private static String format(MonetaryFormat format,
                                 long amount,
                                 boolean showCurrencyCode,
                                 CurrencyDisplayMode mode) {

        if (mode == CurrencyDisplayMode.SATS) {
            return formatUsingSats(amount, showCurrencyCode);
        }

        if (showCurrencyCode) {
            return format.postfixCode().format(Coin.valueOf(amount)).toString();

        } else {
            return format.noCode().format(Coin.valueOf(amount)).toString();
        }
    }

    private static String formatUsingSats(long amount, boolean showCurrencyCode) {
        return amount + (showCurrencyCode ? " SAT" : "");
    }

    @NonNull
    private static MonetaryFormat getFormatWithLocale(MonetaryFormat format) {
        return format.withLocale(Locale.getDefault());
    }

    private BitcoinHelper() {
        throw new AssertionError();
    }
}
