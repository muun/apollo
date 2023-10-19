package io.muun.apollo.presentation.ui.new_operation

import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.common.utils.BitcoinUtils
import timber.log.Timber
import javax.money.MonetaryAmount

class DisplayAmount(
    val amount: BitcoinAmount,
    private val bitcoinUnit: BitcoinUnit,
    private val isSatSelectedAsCurrency: Boolean,
    val isValid: Boolean = true,
) {

    constructor(
        amt: newop.BitcoinAmount,
        btcUnit: BitcoinUnit,
        isSatSelectedAsCurrency: Boolean,
        isValid: Boolean = true,
    ) : this(
        BitcoinAmount.fromLibwallet(amt), btcUnit, isSatSelectedAsCurrency, isValid
    )

    /**
     * Cyclically rotate currencies of a DisplayAmount. Following these rules:
     *
     * - input -> primary (unless input == primary, then btc)
     * - primary -> BTC (unless primary == BTC, then input)
     * - BTC -> input
     */
    fun rotateCurrency(selectedCurrencyCode: String): MonetaryAmount {

        val inputCurrency = amount.inInputCurrency.currency.currencyCode
        val primaryCurrency = amount.inPrimaryCurrency.currency.currencyCode

        val selectedCurrency = if (selectedCurrencyCode == "SAT") {
            "BTC"
        } else {
            selectedCurrencyCode
        }

        @Suppress("CascadeIf")
        if (selectedCurrency == inputCurrency) {
            return if (selectedCurrency == primaryCurrency) {
                BitcoinUtils.satoshisToBitcoins(amount.inSatoshis)
            } else {
                amount.inPrimaryCurrency
            }

        } else if (selectedCurrency == primaryCurrency) {
            return if (selectedCurrency == "BTC") {
                amount.inInputCurrency
            } else {
                BitcoinUtils.satoshisToBitcoins(amount.inSatoshis)
            }

        } else if (selectedCurrency == "BTC") {
            return amount.inInputCurrency // We already know selectedCurrency != inputCurrency
        }

        Timber.i("Bug in rotateCurrency(). $selectedCurrencyCode, $inputCurrency, $primaryCurrency")
        return BitcoinUtils.satoshisToBitcoins(amount.inSatoshis) // Shouldn't really happen
    }

    /**
     * Part of our (ugly) hack to allow SATs as an input currency option. Which should now be
     * contained just here and inside MuunAmountInput.
     * This method answers the question of which BitcoinUnit should be used to format/display btc
     * amounts in a flow where SAT can be chosen as a currency. Usually we would just use the
     * BitcoinUnit User preference, but when on a flowgit  where SAT is chosen as a currency (e.g new
     * operation flow) we override that preference. Following the same logic, if the chosen
     * currency is BTC, but the user bitcoin unit preference is SAT, we prioritize the chosen
     * currency.
     * TODO should we try to encapsulate this INSIDE this class and avoid exposing this method?
     */
    fun getBitcoinUnit(): BitcoinUnit = if (isSatSelectedAsCurrency) {
        BitcoinUnit.SATS
    } else {
        if (amount.inInputCurrency.currency.isBtc()) {
            BitcoinUnit.BTC
        } else {
            bitcoinUnit
        }
    }
}