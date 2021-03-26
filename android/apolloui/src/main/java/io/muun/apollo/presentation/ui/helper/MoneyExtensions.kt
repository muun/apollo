package io.muun.apollo.presentation.ui.helper

import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.BitcoinAmount
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

fun MonetaryAmount.isBtc(): Boolean {
    return currency.isBtc()
}

fun CurrencyUnit.isBtc(): Boolean {
    return currencyCode == "BTC"
}

fun MonetaryAmount.serialize(): String {
    return SerializationUtils.serializeMonetaryAmount(this)
}

fun BitcoinAmount.serialize(): String {
    return SerializationUtils.serializeBitcoinAmount(this)
}