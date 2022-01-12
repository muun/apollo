package io.muun.apollo.presentation.ui.new_operation

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import io.muun.apollo.R
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.PaymentRequestJson
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.utils.getColorCompat
import io.muun.common.api.ExchangeRateWindow
import io.muun.common.bitcoinj.BlockHelpers
import io.muun.common.model.ExchangeRateProvider
import newop.PaymentContext
import newop.PaymentIntent
import org.threeten.bp.ZonedDateTime
import javax.money.MonetaryAmount

private const val CONF_CERTAINTY = 0.75

private const val ARG_PAY_REQ = "payment_request"

fun PaymentRequest.toBundle() = Bundle().apply {
    putString(
        ARG_PAY_REQ, SerializationUtils.serializeJson(PaymentRequestJson::class.java, toJson())
    )
}

object PaymentRequestCompanion {

    @JvmStatic
    fun fromBundle(bundle: Bundle) =
        PaymentRequest.fromJson(
            SerializationUtils.deserializeJson(
                PaymentRequestJson::class.java,
                bundle.getString(ARG_PAY_REQ)
            )
        )
}

fun PaymentIntent.getPaymentType() =
    when {
        uri.invoice != null -> PaymentRequest.Type.TO_LN_INVOICE
        !TextUtils.isEmpty(uri.address) -> PaymentRequest.Type.TO_ADDRESS
        else -> PaymentRequest.Type.TO_CONTACT
    }

fun Activity.toRichText(amt: MonetaryAmount, btcUnit: BitcoinUnit, isValid: Boolean): CharSequence =
    MoneyHelper.toLongRichText(
        amt,
        getColorCompat(if (isValid) R.color.text_primary_color else R.color.red),
        getColorCompat(if (isValid) R.color.text_secondary_color else R.color.red),
        btcUnit,
        applicationContext.locale()
    )

fun Array<View>.changeVisibility(visibility: Int) {
    for (aView in this) {
        aView.visibility = visibility
    }
}

fun MonetaryAmount.toLibwallet() =
    newop.MonetaryAmount(number.toString(), currency.currencyCode)

fun PaymentContext.buildExchangeRateProvider(): ExchangeRateProvider {
    val rates = HashMap<String, Double>()
    val currencies = exchangeRateWindow.currencies()
    for (i in 0 until currencies.length()) {
        rates[currencies.get(i)] = exchangeRateWindow.rate(currencies.get(i))
    }

    val windowId = exchangeRateWindow.windowId
    return ExchangeRateProvider(
        // TODO: pass exchangeRateWindo fetch date to libwallet to obtain it here?
        ExchangeRateWindow(windowId, ApolloZonedDateTime.of(ZonedDateTime.now()), rates)
    )
}

fun estimateTimeInMs(numBlocks: Int) =
    BlockHelpers.timeInSecsForBlocksWithCertainty(numBlocks, CONF_CERTAINTY).toLong() * 1000