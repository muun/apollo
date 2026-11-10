package io.muun.apollo.domain.model.marketplace

import javax.money.MonetaryAmount

data class SecurityCardMarketplace(
    val providers: List<SecurityCardProvider>,
) {

    data class SecurityCardProvider(
        val uuid: String,
        val name: String,
        val lightTheme: SecurityCardProviderTheme,
        val darkTheme: SecurityCardProviderTheme,
        val securityCards: List<SecurityCard>,
        val cardPrice: MonetaryAmount,
        val cardPriceInSats: Long,
        val shippingPrice: MonetaryAmount,
        val shippingPriceInSats: Long,
    )

    data class SecurityCard(
        val uuid: String,
        val imageUrl: String,
        val hasStock: Boolean,
        val material: SecurityCardMaterial,
    ) {

        val isOutOfStock: Boolean
            get() = !hasStock
    }
}
