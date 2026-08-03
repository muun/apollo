package io.muun.apollo.presentation.ui.security_cards_full_specs

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.AdditionalInfo
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityCardsFullSpecsViewModel @AssistedInject constructor(
    @Assisted val card: SecurityCard,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(card: SecurityCard): SecurityCardsFullSpecsViewModel
    }

    data class ViewState(
        val items: List<SpecListItem>,
    )

    private val _viewState = MutableStateFlow<ViewState>(
        ViewState(
            items = buildSpecList(card),
        )
    )
    val viewState: StateFlow<ViewState> = _viewState
}

private fun buildSpecList(
    card: SecurityCard,
): List<SpecListItem> = buildList {
    add(SpecListItem.CardImage(card.imageRes))
    card.specs.filter { it.key != "primary" }.forEach { (category, specs) ->
        add(SpecListItem.SectionHeader(category.capitalize()))
        specs.forEach { spec ->
            add(SpecListItem.Row(spec.iconRes, spec.label, spec.value))
        }
    }
}

sealed interface SpecListItem {

    data class CardImage(
        @DrawableRes val imageRes: Int,
    ) : SpecListItem

    data class SectionHeader(
        val title: String,
    ) : SpecListItem

    data class Row(
        @DrawableRes val iconRes: Int,
        val label: String,
        val value: String,
        val additionalInfo: AdditionalInfo? = null,
    ) : SpecListItem
}
