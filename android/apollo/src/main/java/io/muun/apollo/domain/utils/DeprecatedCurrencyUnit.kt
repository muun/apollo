package io.muun.apollo.domain.utils

import javax.money.CurrencyContext
import javax.money.CurrencyContextBuilder
import javax.money.CurrencyUnit

/**
 * This is our "placeholder" ad-hoc CurrencyUnit implementation. It helps us in the edge case where
 * a user's primary currency is no longer supported (by the Moneta lib) after an app or OS update,
 * or if user has changed to a device that no longer supports their primary currency. Notice this
 * situation also affects operation in user's operation history (e.g an operation amount is recorded
 * in btc and the user's primary currency at the time of the operation).
 *
 * Note: main goal for this class is to be able to distinguish a primary currency when its
 * deprecated (via instance of) and to still be able to display amounts in
 * {@link #wrappedCurrencyCode} in the user's operation history.
 */
class DeprecatedCurrencyUnit(private val wrappedCurrencyCode: String) : CurrencyUnit {

    override fun compareTo(other: CurrencyUnit?): Int {
        if (other == null) {
            return -1
        }

        // wrappedCurrencyCode is deprecated so we shouldn't find it "in the wild"
        return wrappedCurrencyCode.compareTo(other.currencyCode)
    }

    override fun getCurrencyCode(): String =
        wrappedCurrencyCode

    override fun getNumericCode(): Int =
        0

    /**
     * Using 0 default fraction digits as we expect currencies to be deprecated to be high
     * inflationary or otherwise very devalued.
     */
    override fun getDefaultFractionDigits(): Int =
        0

    override fun getContext(): CurrencyContext =
        CurrencyContextBuilder.of("FAKE_PROVIDER").build()
}