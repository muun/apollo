package io.muun.apollo.presentation.ui.bundler

import android.os.Bundle
import icepick.Bundler
import io.muun.apollo.domain.model.lnurl.LnUrlError

class LnUrlWithdrawErrorBundler : Bundler<LnUrlError> {

    override fun put(key: String, error: LnUrlError?, bundle: Bundle) {
        bundle.putString(key, error?.serialize())
    }

    override fun get(key: String, bundle: Bundle): LnUrlError? {
        val serialization = bundle.getString(key) ?: return null

        return LnUrlError.deserialize(serialization)
    }
}