package io.muun.apollo.presentation.ui.select_amount

import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.common.model.ExchangeRateProvider
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

interface SelectAmountView : BaseView {

    fun setBitcoinUnit(bitcoinUnit: BitcoinUnit)

    fun setExchangeRateProvider(exchangeRateProvider: ExchangeRateProvider)

    fun initializeAmountInput(primaryCurrency: CurrencyUnit)

    fun setSecondaryAmount(amount: MonetaryAmount)

    fun hideSecondaryAmount()

    fun setAmountError(showAmountError: Boolean)

    fun finishWithResult(resultCode: Int, amount: BitcoinAmount?)

}