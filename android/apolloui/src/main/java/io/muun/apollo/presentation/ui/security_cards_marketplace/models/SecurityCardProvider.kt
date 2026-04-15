package io.muun.apollo.presentation.ui.security_cards_marketplace.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.javamoney.moneta.Money
import java.math.BigDecimal
import javax.money.MonetaryAmount

@Parcelize
data class SecurityCardProvider(
    val name: String,
    val description: String,
    val securityCards: List<SecurityCard>,
    val currencyCode: String,
) : Parcelable

// Mocking stuff, not real implementation.
fun SecurityCardProvider.cardCost(card: SecurityCard): MonetaryAmount {
    val cardPosition = securityCards.indexOf(card) + 1
    return Money.of(BigDecimal.valueOf(cardPosition * 10_000L), currencyCode)
}

fun SecurityCardProvider.shippingAndTaxesCost(card: SecurityCard): MonetaryAmount {
    val cardPosition = securityCards.indexOf(card) + 1
    return Money.of(BigDecimal.valueOf(cardPosition * 1_500L), currencyCode)
}
