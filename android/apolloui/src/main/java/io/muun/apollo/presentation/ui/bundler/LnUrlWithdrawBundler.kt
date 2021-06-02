package io.muun.apollo.presentation.ui.bundler

import android.os.Bundle
import icepick.Bundler
import io.muun.apollo.domain.model.LnUrlWithdraw

class LnUrlWithdrawBundler: Bundler<LnUrlWithdraw> {

    override fun put(key: String, lnUrlWithdraw: LnUrlWithdraw?, bundle: Bundle) {
        bundle.putString(key, lnUrlWithdraw?.serialize())
    }

    override fun get(key: String, bundle: Bundle): LnUrlWithdraw? {
        val serialization = bundle.getString(key) ?: return null

        return LnUrlWithdraw.deserialize(serialization)
    }
}