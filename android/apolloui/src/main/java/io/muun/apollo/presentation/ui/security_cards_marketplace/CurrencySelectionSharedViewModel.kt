package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.utils.rotate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CurrencySelectionSharedViewModel @AssistedInject constructor(
    @Assisted val initialCurrentCurrency: CurrentCurrency,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(initialCurrentCurrency: CurrentCurrency): CurrencySelectionSharedViewModel
    }

    private val _selectedCurrency = MutableStateFlow(initialCurrentCurrency)
    val selectedCurrency: StateFlow<CurrentCurrency> = _selectedCurrency

    fun rotateCurrencySelection() {
        _selectedCurrency.tryEmit(_selectedCurrency.value.rotate())
    }
}
