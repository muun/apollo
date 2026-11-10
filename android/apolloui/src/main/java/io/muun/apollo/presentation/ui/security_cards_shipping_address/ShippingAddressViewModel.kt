package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.data.preferences.ConnectedToWebsiteDialogSeenRepository
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.presentation.ui.bundler.MarketplaceCountryCodeParceler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

class ShippingAddressViewModel @AssistedInject constructor(
    @Assisted("countryCode") private val initialCountryCode: MarketplaceCountryCode,
    @Assisted("providerUuid") private val providerUuid: String,
    @Assisted private val savedStateHandle: SavedStateHandle,
    private val connectedToWebsiteDialogSeenRepository: ConnectedToWebsiteDialogSeenRepository,
) : ViewModel() {

    companion object {
        private const val VIEW_STATE_KEY = "VIEW_STATE_KEY"
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("countryCode") countryCode: MarketplaceCountryCode,
            @Assisted("providerUuid") providerUuid: String,
            savedStateHandle: SavedStateHandle,
        ): ShippingAddressViewModel
    }

    @Parcelize
    @TypeParceler<MarketplaceCountryCode, MarketplaceCountryCodeParceler>
    data class ViewState(
        val fullName: FormItemValue = FormItemValue(),
        val email: FormItemValue = FormItemValue(),
        val address1: FormItemValue = FormItemValue(),
        val address2: FormItemValue = FormItemValue(),
        val countryCode: MarketplaceCountryCode,
        val city: FormItemValue = FormItemValue(),
        val state: FormItemValue = FormItemValue(),
        val zipCode: FormItemValue = FormItemValue(),
    ) : Parcelable {

        val hasErrors: Boolean
            get() = listOf(email, fullName, address1, city, state, zipCode)
                .any { it.error != null }
    }

    sealed interface ViewEvent {

        object NavigateToCheckout : ViewEvent

        object ScrollToFirstError : ViewEvent
    }

    private val _viewState = savedStateHandle.getMutableStateFlow(
        VIEW_STATE_KEY,
        ViewState(countryCode = initialCountryCode)
    )
    val viewState: StateFlow<ViewState> = _viewState

    private val _viewEvent = MutableSharedFlow<ViewEvent>(replay = 0, extraBufferCapacity = 1)
    val viewEvent: SharedFlow<ViewEvent> = _viewEvent

    fun shouldShowConnectedToWebsiteDialogOnStart(): Boolean {
        return !connectedToWebsiteDialogSeenRepository.isSeenFor(providerUuid)
    }

    fun saveDontShowConnectedToWebsiteDialogOnStart() {
        connectedToWebsiteDialogSeenRepository.markSeenFor(providerUuid)
    }

    fun setFullName(value: String) {
        _viewState.update { it.copy(fullName = FormItemValue(value)) }
    }

    fun setEmail(value: String) {
        _viewState.update { it.copy(email = FormItemValue(value)) }
    }

    fun setAddress1(value: String) {
        _viewState.update { it.copy(address1 = FormItemValue(value)) }
    }

    fun setAddress2(value: String) {
        _viewState.update { it.copy(address2 = FormItemValue(value)) }
    }

    fun setShippingCountryCode(countryCode: MarketplaceCountryCode) {
        _viewState.update { it.copy(countryCode = countryCode) }
    }

    fun setShippingCity(value: String) {
        _viewState.update { it.copy(city = FormItemValue(value)) }
    }

    fun setShippingState(value: String) {
        _viewState.update { it.copy(state = FormItemValue(value)) }
    }

    fun setShippingZipCode(value: String) {
        _viewState.update { it.copy(zipCode = FormItemValue(value)) }
    }

    fun submit() {
        val validated = _viewState.value.validate()

        if (validated.hasErrors) {
            _viewState.value = validated
            _viewEvent.tryEmit(ViewEvent.ScrollToFirstError)
            return
        }

        _viewEvent.tryEmit(ViewEvent.NavigateToCheckout)
    }

    private fun ViewState.validate(): ViewState {
        return copy(
            email = email.notBlank().validEmail(),
            fullName = fullName.notBlank(),
            address1 = address1.notBlank(),
            city = city.notBlank(),
            state = state.notBlank(),
            zipCode = zipCode.notBlank(),
        )
    }
}
