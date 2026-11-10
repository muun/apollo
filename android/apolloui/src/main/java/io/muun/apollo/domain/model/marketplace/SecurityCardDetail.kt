package io.muun.apollo.domain.model.marketplace

import javax.money.MonetaryAmount

data class SecurityCardDetail(
    val provider: SecurityCardProvider,
    val card: SecurityCard,
) {

    data class SecurityCardProvider(
        val uuid: String,
        val name: String,
        val lightTheme: SecurityCardProviderTheme,
        val darkTheme: SecurityCardProviderTheme,
        val websiteUrl: String,
        val shippingCountryCode: MarketplaceCountryCode,
        val minShippingTimeDays: Int,
        val maxShippingTimeDays: Int,
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
    )
}
