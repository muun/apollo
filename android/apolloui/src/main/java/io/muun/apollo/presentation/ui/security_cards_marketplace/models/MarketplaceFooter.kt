package io.muun.apollo.presentation.ui.security_cards_marketplace.models

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.javamoney.moneta.Money
import java.math.BigDecimal
import javax.money.MonetaryAmount

@Parcelize
@TypeParceler<MonetaryAmount, MonetaryAmountParceler>()
data class MarketplaceFooter(
    val cardCost: MonetaryAmount,
    val shippingAndTaxesCost: MonetaryAmount,
    val currentCurrency: CurrentCurrency
) : Parcelable {

    enum class CurrentCurrency {
        PRIMARY, BTC;
    }
}

object MonetaryAmountParceler : Parceler<MonetaryAmount?> {

    override fun create(parcel: Parcel): MonetaryAmount? {
        val currency = parcel.readString() ?: return null
        val amount = parcel.readSerializable() as BigDecimal
        return Money.of(amount, currency)
    }

    override fun MonetaryAmount?.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this?.currency?.currencyCode)
        parcel.writeSerializable(this?.number?.numberValue(BigDecimal::class.java))
    }
}
