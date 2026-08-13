package io.muun.apollo.presentation.ui.security_cards_full_specs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard

class SecurityCardsFullSpecsViewModelFactory(
    private val card: SecurityCard,
    private val assistedFactory: SecurityCardsFullSpecsViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            card = card,
        ) as T
    }
}
