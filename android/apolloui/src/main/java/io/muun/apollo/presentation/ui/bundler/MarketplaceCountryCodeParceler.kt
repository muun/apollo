package io.muun.apollo.presentation.ui.bundler

import android.os.Parcel
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import kotlinx.parcelize.Parceler

/**
 * Parcels the domain value object as its raw value, rebuilding it through
 * the validating constructor on read. The adapter lives here because the
 * domain layer stays android-free.
 */
object MarketplaceCountryCodeParceler : Parceler<MarketplaceCountryCode> {

    override fun create(parcel: Parcel): MarketplaceCountryCode {
        return MarketplaceCountryCode(requireNotNull(parcel.readString()))
    }

    override fun MarketplaceCountryCode.write(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
    }
}
