package io.muun.apollo.domain.model.marketplace

data class SecurityCardFullSpecs(
    val provider: SecurityCardProvider,
    val card: SecurityCard,
) {

    data class SecurityCardProvider(
        val uuid: String,
        val name: String,
        val lightTheme: SecurityCardProviderTheme,
        val darkTheme: SecurityCardProviderTheme,
        val shippingCountryCode: MarketplaceCountryCode,
        val minShippingTimeDays: Int,
        val maxShippingTimeDays: Int,
    )

    data class SecurityCard(
        val uuid: String,
        val imageUrl: String,
        val hasStock: Boolean,
        val widthMm: Float,
        val heightMm: Float,
        val material: SecurityCardMaterial,
        val thicknessMm: Float,
        val weightGrams: Float,
        val secureElement: SecurityCardSecureElement,
    )
}
