package io.muun.apollo.presentation.ui.bundler

import android.os.Bundle
import icepick.Bundler
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.CountryInfo
import io.muun.apollo.presentation.ui.helper.serialize
import javax.money.MonetaryAmount

class MonetaryAmountBundler : Bundler<MonetaryAmount> {

    override fun put(key: String, amount: MonetaryAmount?, bundle: Bundle) {
        bundle.putString(key, amount?.serialize())
    }

    override fun get(key: String, bundle: Bundle): MonetaryAmount? {
        val serialization = bundle.getString(key) ?: return null

        return SerializationUtils.deserializeMonetaryAmount(serialization)
    }
}