package io.muun.apollo.presentation.ui.utils

import javax.money.CurrencyContext
import javax.money.CurrencyContextBuilder
import javax.money.CurrencyUnit

class FakeCurrencyUnit : CurrencyUnit {

    override fun compareTo(other: CurrencyUnit?): Int {
        if (other == null) {
            return -1
        }

        return numericCode.compareTo(other.numericCode)
    }

    override fun getCurrencyCode(): String =
        "FAKE"

    override fun getNumericCode(): Int =
        0

    override fun getDefaultFractionDigits(): Int =
        0

    override fun getContext(): CurrencyContext =
        CurrencyContextBuilder.of("FAKE_PROVIDER").build()
}