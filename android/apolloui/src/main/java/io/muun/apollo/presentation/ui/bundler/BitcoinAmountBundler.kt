package io.muun.apollo.presentation.ui.bundler

import android.os.Bundle
import icepick.Bundler
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.CountryInfo
import io.muun.apollo.presentation.ui.helper.serialize
import javax.money.MonetaryAmount

class BitcoinAmountBundler : Bundler<BitcoinAmount> {

    override fun put(key: String, amount: BitcoinAmount?, bundle: Bundle) {
        bundle.putString(key, amount?.serialize())
    }

    override fun get(key: String, bundle: Bundle): BitcoinAmount? {
        val serialization = bundle.getString(key) ?: return null

        return SerializationUtils.deserializeBitcoinAmount(serialization)
    }
}