package io.muun.apollo.presentation.ui.security_cards_marketplace.models

import android.os.Parcel
import android.os.Parcelable
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.domain.model.marketplace.SecurityCardMaterial
import io.muun.apollo.domain.model.marketplace.SecurityCardProviderTheme
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.javamoney.moneta.Money
import java.math.BigDecimal
import javax.money.MonetaryAmount

@Parcelize
@TypeParceler<SecurityCardMarketplace.SecurityCardProvider, SecurityCardProviderParceler>()
data class SecurityCardProviderParcelable(
    val provider: SecurityCardMarketplace.SecurityCardProvider,
) : Parcelable

private object SecurityCardProviderParceler :
    Parceler<SecurityCardMarketplace.SecurityCardProvider> {

    override fun create(parcel: Parcel): SecurityCardMarketplace.SecurityCardProvider {
        val uuid = requireNotNull(parcel.readString())
        val name = requireNotNull(parcel.readString())
        val lightTheme = parcel.readTheme()
        val darkTheme = parcel.readTheme()
        val securityCards = List(parcel.readInt()) { parcel.readSecurityCard() }
        val cardPrice = parcel.readMonetaryAmount()
        val cardPriceInSats = parcel.readLong()
        val shippingPrice = parcel.readMonetaryAmount()
        val shippingPriceInSats = parcel.readLong()

        return SecurityCardMarketplace.SecurityCardProvider(
            uuid = uuid,
            name = name,
            lightTheme = lightTheme,
            darkTheme = darkTheme,
            securityCards = securityCards,
            cardPrice = cardPrice,
            cardPriceInSats = cardPriceInSats,
            shippingPrice = shippingPrice,
            shippingPriceInSats = shippingPriceInSats,
        )
    }

    override fun SecurityCardMarketplace.SecurityCardProvider.write(parcel: Parcel, flags: Int) {
        parcel.writeString(uuid)
        parcel.writeString(name)
        parcel.writeTheme(lightTheme)
        parcel.writeTheme(darkTheme)
        parcel.writeInt(securityCards.size)
        for (securityCard in securityCards) {
            parcel.writeSecurityCard(securityCard)
        }
        parcel.writeMonetaryAmount(cardPrice)
        parcel.writeLong(cardPriceInSats)
        parcel.writeMonetaryAmount(shippingPrice)
        parcel.writeLong(shippingPriceInSats)
    }

    private fun Parcel.readTheme(): SecurityCardProviderTheme {
        return SecurityCardProviderTheme(
            primaryColorHex = readInt(),
            surfaceColorHex = readInt(),
        )
    }

    private fun Parcel.writeTheme(theme: SecurityCardProviderTheme) {
        writeInt(theme.primaryColorHex)
        writeInt(theme.surfaceColorHex)
    }

    private fun Parcel.readSecurityCard(): SecurityCardMarketplace.SecurityCard {
        return SecurityCardMarketplace.SecurityCard(
            uuid = requireNotNull(readString()),
            imageUrl = requireNotNull(readString()),
            hasStock = readInt() == 1,
            material = SecurityCardMaterial.valueOf(requireNotNull(readString())),
        )
    }

    private fun Parcel.writeSecurityCard(securityCard: SecurityCardMarketplace.SecurityCard) {
        writeString(securityCard.uuid)
        writeString(securityCard.imageUrl)
        writeInt(if (securityCard.hasStock) 1 else 0)
        writeString(securityCard.material.name)
    }

    private fun Parcel.readMonetaryAmount(): MonetaryAmount {
        val currencyCode = requireNotNull(readString())
        val amount = readSerializable() as BigDecimal

        return Money.of(amount, currencyCode)
    }

    private fun Parcel.writeMonetaryAmount(monetaryAmount: MonetaryAmount) {
        writeString(monetaryAmount.currency.currencyCode)
        writeSerializable(monetaryAmount.number.numberValue(BigDecimal::class.java))
    }
}
