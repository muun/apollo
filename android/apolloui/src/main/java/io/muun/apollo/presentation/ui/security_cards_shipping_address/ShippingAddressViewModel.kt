package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.parcelize.Parcelize

class ShippingAddressViewModel @AssistedInject constructor(
    @Assisted private val initialCountryInfo: CountryInfo,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val VIEW_STATE_KEY = "VIEW_STATE_KEY"
    }

    @AssistedFactory
    interface Factory {
        fun create(
            countryInfo: CountryInfo,
            savedStateHandle: SavedStateHandle,
        ): ShippingAddressViewModel
    }

    @Parcelize
    data class ViewState(
        val fullName: String = "",
        val email: String = "",
        val shippingAddress: String = "",
        val countryInfo: CountryInfo,
        val city: String = "",
        val state: String = "",
        val zipCode: String = "",
    ): Parcelable {

        val isFormComplete: Boolean
            get() = fullName.isNotBlank()
                && email.isNotBlank()
                && shippingAddress.isNotBlank()
                && city.isNotBlank()
                && state.isNotBlank()
                && zipCode.isNotBlank()
    }

    private val _viewState = savedStateHandle.getMutableStateFlow(
        VIEW_STATE_KEY,
        ViewState(countryInfo = initialCountryInfo)
    )
    val viewState: StateFlow<ViewState> = _viewState

    fun setFullName(value: String) {
        _viewState.update { it.copy(fullName = value) }
    }

    fun setEmail(value: String) {
        _viewState.update { it.copy(email = value) }
    }

    fun setShippingAddress(value: String) {
        _viewState.update { it.copy(shippingAddress = value) }
    }

    fun setShippingCountry(country: CountryInfo) {
        _viewState.update { it.copy(countryInfo = country) }
    }

    fun setShippingCity(value: String) {
        _viewState.update { it.copy(city = value) }
    }

    fun setShippingState(value: String) {
        _viewState.update { it.copy(state = value) }
    }

    fun setShippingZipCode(value: String) {
        _viewState.update { it.copy(zipCode = value) }
    }
}
