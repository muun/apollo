package io.muun.apollo.presentation.ui.select_amount

import android.app.Activity
import android.os.Bundle
import io.muun.apollo.data.getRateWindow
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_AMOUNT_PICKER
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.common.model.ExchangeRateProvider
import io.muun.common.utils.BitcoinUtils
import org.javamoney.moneta.Money
import rx.Observable
import javax.inject.Inject
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount
import javax.validation.constraints.NotNull

@PerActivity
class SelectAmountPresenter @Inject constructor(
    private val exchangeRateSelector: ExchangeRateSelector,
    private val bitcoinUnitSel: BitcoinUnitSelector
) : BasePresenter<SelectAmountView>() {

    companion object {
        private val MAX_BTC_AMOUNT = Money.of(21_000_000, "BTC")
        private val MIN_BTC_AMOUNT = Money.of(0.00000001, "BTC")
        private val DUST_AMOUNT = BitcoinUtils.satoshisToBitcoins(BitcoinUtils.DUST_IN_SATOSHIS)
    }

    // State: presenter has to be marked as persistent (has a MuunAmountInput, which,
    // after a currency change can wreak havoc if all this state isn't persisted, in particular
    // rateProvider).

    private lateinit var rateProvider: ExchangeRateProvider

    private lateinit var primaryCurrency: CurrencyUnit

    private var amount: MonetaryAmount? = null

    private var isOnChainAmount: Boolean = true

    private fun isInitialized(): Boolean = ::rateProvider.isInitialized

    override fun setUp(@NotNull arguments: Bundle) {
        super.setUp(arguments)

        isOnChainAmount = arguments.getBoolean(SelectAmountActivity.IS_BTC_ON_CHAIN)

        setUpExchangeRates()
    }

    private fun setUpExchangeRates() {
        val observable: Observable<*> = exchangeRateSelector
            .watchLatest()
            .compose(getAsyncExecutor())
            .doOnNext { provider: ExchangeRateProvider -> onExchangeRatesChange(provider) }
        subscribeTo(observable)
    }

    private fun onExchangeRatesChange(exchangeRateProvider: ExchangeRateProvider) {
        val isBeingInitialized = !isInitialized()
        exchangeRateSelector.fixWindow(exchangeRateProvider.getRateWindow())
        rateProvider = exchangeRateProvider
        view.setExchangeRateProvider(exchangeRateProvider)

        primaryCurrency = userSel.get().getPrimaryCurrency(rateProvider)

        // This needs to happen AFTER rateProvider init due to the side effects of
        // initializeAmountInput (e.g updateAmount())
        // Also, initialize AmountInput just once and not on every exchangeRates change
        if (isBeingInitialized) {
            view.initializeAmountInput(primaryCurrency, bitcoinUnitSel.get())
        }
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return S_AMOUNT_PICKER()
    }

    fun updateAmount(amount: MonetaryAmount) {
        this.amount = amount
        updateSecondaryAmount(amount)

        val amountTooHigh = rateProvider.convert(amount, "BTC").isGreaterThan(MAX_BTC_AMOUNT)

        val minValidAmount = if (isOnChainAmount) {
            DUST_AMOUNT
        } else {
            MIN_BTC_AMOUNT
        }

        val amountTooLow = amount.isBtc() && amount.isLessThan(minValidAmount)
            ||  rateProvider.convert(amount, "BTC").isLessThan(minValidAmount)

        // Zero is a valid value, it means we want to leave the amount empty
        view.setAmountError(!amount.isZero && (amountTooHigh || amountTooLow))
    }

    private fun updateSecondaryAmount(amount: MonetaryAmount) {
        if (primaryCurrency.isBtc()) {
            view.hideSecondaryAmount()
            return
        }

        val secondaryAmount = if (amount.isBtc()) {
            rateProvider.convert(amount, primaryCurrency.currencyCode)

        } else {
            rateProvider.convert(amount, "BTC")
        }

        view.setSecondaryAmount(secondaryAmount)
    }

    fun confirmSelectedAmount() {
        if (isInitialized()) {
            view.finishWithResult(
                Activity.RESULT_OK,
                BitcoinAmount(
                    BitcoinUtils.bitcoinsToSatoshis(rateProvider.convert(amount, "BTC")),
                    amount,
                    amount
                )
            )
        }
    }
}