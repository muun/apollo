package io.muun.apollo.presentation.ui.new_operation

import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.common.utils.BitcoinUtils
import org.assertj.core.api.Assertions.assertThat
import org.javamoney.moneta.Money
import org.junit.Test
import javax.money.CurrencyUnit
import javax.money.Monetary

internal class DisplayAmountTest {

    private val usd = Monetary.getCurrency("USD")
    private val ars = Monetary.getCurrency("ARS")
    private val btc = Monetary.getCurrency("BTC")

    class InputData(val inputCurrency: CurrencyUnit, val primaryCurrency: CurrencyUnit)
    class ExpectedOutput(val rotatedCurrency: Array<CurrencyUnit>)

    @Test
    fun testRotateCurrency() {

        testRotate(InputData(usd, ars), ExpectedOutput(arrayOf(ars, btc, usd)))
        testRotate(InputData(usd, usd), ExpectedOutput(arrayOf(btc, usd, btc)))
        testRotate(InputData(usd, btc), ExpectedOutput(arrayOf(btc, usd, btc)))
        testRotate(InputData(btc, usd), ExpectedOutput(arrayOf(usd, btc, usd)))
    }

    private fun testRotate(inputData: InputData, expectedOutput: ExpectedOutput) {

        val bitcoinAmount = BitcoinAmount(
            BitcoinUtils.bitcoinsToSatoshis(Money.of(1, btc)),
            Money.of(1, inputData.inputCurrency),
            Money.of(1, inputData.primaryCurrency)
        )

        val displayAmount = DisplayAmount(bitcoinAmount, BitcoinUnit.BTC, false)

        var rotatedAmount = displayAmount.rotateCurrency(inputData.inputCurrency.currencyCode)
        assertThat(rotatedAmount.currency).isEqualTo(expectedOutput.rotatedCurrency[0])

        rotatedAmount = displayAmount.rotateCurrency(rotatedAmount.currency.currencyCode)
        assertThat(rotatedAmount.currency).isEqualTo(expectedOutput.rotatedCurrency[1])

        rotatedAmount = displayAmount.rotateCurrency(rotatedAmount.currency.currencyCode)
        assertThat(rotatedAmount.currency).isEqualTo(expectedOutput.rotatedCurrency[2])

        // This shouldn't really happen but in case of error we fallback to BTC
        rotatedAmount = displayAmount.rotateCurrency("COP")
        assertThat(rotatedAmount.currency).isEqualTo(btc)
    }

    @Test
    fun testDisplayOfBitcoinAmounts() {
        val nonBtc = usd
        testDisplay(btc, BitcoinUnit.BTC, false, BitcoinUnit.BTC)
        testDisplay(nonBtc, BitcoinUnit.BTC, false, BitcoinUnit.BTC)
        testDisplay(btc, BitcoinUnit.BTC, true, BitcoinUnit.SATS)
        testDisplay(nonBtc, BitcoinUnit.BTC, true, BitcoinUnit.SATS)
        testDisplay(btc, BitcoinUnit.SATS, false, BitcoinUnit.BTC)
        testDisplay(nonBtc, BitcoinUnit.SATS, false, BitcoinUnit.SATS)
        testDisplay(btc, BitcoinUnit.SATS, true, BitcoinUnit.SATS)
        testDisplay(nonBtc, BitcoinUnit.SATS, true, BitcoinUnit.SATS)
    }

    private fun testDisplay(
        inputCurrency: CurrencyUnit,
        btcUnit: BitcoinUnit,
        satAsCurrency: Boolean,
        expected: BitcoinUnit,
    ) {
        val bitcoinAmount = BitcoinAmount(
            BitcoinUtils.bitcoinsToSatoshis(Money.of(1, btc)),
            Money.of(1, inputCurrency),
            Money.of(1, ars)
        )
        val displayAmount = DisplayAmount(bitcoinAmount, btcUnit, satAsCurrency)
        assertThat(displayAmount.getBitcoinUnit()).isEqualTo(expected)
    }
}