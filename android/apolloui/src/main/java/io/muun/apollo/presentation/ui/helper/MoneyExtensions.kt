package io.muun.apollo.presentation.ui.helper

import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.BitcoinAmount
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

/**
 * Return whether the specified amount is in Bitcoin or not.
 */
fun MonetaryAmount.isBtc(): Boolean {
    return currency.isBtc()
}

/**
 * Return whether the specified currency is Bitcoin or not.
 */
fun CurrencyUnit.isBtc(): Boolean {
    return currencyCode == "BTC"
}

fun MonetaryAmount.serialize(): String {
    return SerializationUtils.serializeMonetaryAmount(this)
}

fun BitcoinAmount.serialize(): String {
    return SerializationUtils.serializeBitcoinAmount(this)
}