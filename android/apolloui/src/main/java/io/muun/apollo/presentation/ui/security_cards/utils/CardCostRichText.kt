package io.muun.apollo.presentation.ui.security_cards.utils

import android.content.Context
import android.text.TextUtils
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.view.RichText
import javax.money.MonetaryAmount

/**
 * Format a card cost with the currency code at half the amount's size, per the marketplace
 * designs. Text color comes from the TextView: amount and code share it.
 */
fun MonetaryAmount.toCardCostRichText(context: Context): CharSequence {
    val locale = context.locale()

    return TextUtils.concat(
        MoneyHelper.formatLongMonetaryAmount(
            this,
            showCurrencyCode = false,
            BitcoinUnit.BTC,
            locale,
        ),
        " ",
        RichText(MoneyHelper.formatCurrency(currency, BitcoinUnit.BTC)).setRelativeSize(0.5f),
    )
}
