package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode

class ShippingAddressViewModelFactory(
    private val countryCode: MarketplaceCountryCode,
    private val providerUuid: String,
    private val assistedFactory: ShippingAddressViewModel.Factory,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null,
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            countryCode = countryCode,
            providerUuid = providerUuid,
            savedStateHandle = handle,
        ) as T
    }
}
