package io.muun.apollo.presentation.ui.security_cards_marketplace.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import javax.money.MonetaryAmount

data class MarketplaceFooter(
    val cardCost: MonetaryAmount,
    val cardCostInSats: Long,
    val shippingAndTaxesCost: MonetaryAmount,
    val shippingAndTaxesCostInSats: Long,
    val currentCurrency: CurrentCurrency,
) {

    @Parcelize
    enum class CurrentCurrency : Parcelable {
        PRIMARY, BTC;
    }
}
