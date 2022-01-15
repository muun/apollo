package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.presentation.ui.new_operation.NewOperationStep
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import io.muun.apollo.utils.WithMuunInstrumentationHelpers.Companion.moneyEqualsRoundingMarginBTC
import io.muun.apollo.utils.WithMuunInstrumentationHelpers.Companion.moneyEqualsRoundingMarginFiat
import io.muun.apollo.utils.screens.RecommendedFeeScreen.OnScreenFeeOption
import io.muun.common.model.DebtType
import io.muun.common.utils.BitcoinUtils
import io.muun.common.utils.LnInvoice
import org.assertj.core.api.Assertions.assertThat
import javax.money.MonetaryAmount

class NewOperationScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    val confirmedAmount get() =
        id(R.id.selected_amount).text.toMoney()

    val confirmedFee get() =
        id(R.id.fee_amount).text.toMoney()

    val confirmedDescription get() =
        id(R.id.notes_content).text

    val confirmedTotal get() =
        id(R.id.total_amount).text.toMoney()

    fun waitUntilVisible() {
        button(R.id.muun_next_step_button).waitForExists(15000)
    }

    fun assertSubmitIsDisabled() =
        button(R.id.muun_next_step_button).assertDisabled()

    fun fillForm(amount: MonetaryAmount?, description: String?) {

        checkInputAmountCurrenciesMatch()

        // Enter the requested data, moving forward:
        if (amount != null) {
            editAmount(amount)
            goNext()
        }

        if (description != null) {
            editDescription(description)
            goNext()
        }

        checkConfirmedData(amount, description)
    }

    fun fillFormUsingAllFunds(description: String?): MonetaryAmount {
        checkInputAmountCurrenciesMatch()
        confirmUseAllFunds()

        if (description != null) {
            editDescription(description)
            goNext()
        }

        checkConfirmedData(description = description)

        return confirmedAmount
    }

    fun fillForm(
        invoice: LnInvoice,
        description: String? = null,
        debtType: DebtType = DebtType.NONE
    ) {

        waitForResolveOperationUri()

        checkInputAmountCurrenciesMatch()

        if (description != null) {
            editDescription(description)
            goNext()
        }

        checkConfirmedData(invoice, description ?: invoice.description, debtType)
    }

    fun goToEditFee() =
        id(R.id.fee_label).click()

    fun rotateAmountCurrencies() {
        id(R.id.selected_amount).click()
    }

    fun submit() {
        checkStep(NewOperationStep.CONFIRM)
        goNext()
    }

    private fun checkInputAmountCurrenciesMatch() {
        val inputCurrency = id(R.id.currency_code).text
        val totalBalanceCurrency = id(R.id.secondary_amount).text.split(" ")[2]

        assertThat(inputCurrency).isEqualTo(totalBalanceCurrency)
    }

    private fun editAmount(amount: MonetaryAmount) {
        checkStep(NewOperationStep.ENTER_AMOUNT)

        id(R.id.currency_code).click()
        labelWith(amount.currency.currencyCode).click()

        id(R.id.muun_amount).text = amount.number.toString()
    }

    private fun editDescription(description: String) {
        checkStep(NewOperationStep.ENTER_DESCRIPTION)

        id(R.id.muun_note_input).text = description
    }

    private fun confirmUseAllFunds() {
        id(R.id.use_all_funds).click()
    }

    private fun goNext() {
        pressMuunButton(R.id.muun_next_step_button)
    }

    fun checkConfirmedData(
        amount: MonetaryAmount? = null,
        description: String? = null,
        fee: OnScreenFeeOption? = null
    ) {

        checkStep(NewOperationStep.CONFIRM)

        assertThat(confirmedDescription).isNotEmpty()
        assertMoneyEqualsWithRoundingHack(confirmedAmount.add(confirmedFee), confirmedTotal)

        if (amount != null) {
            assertMoneyEqualsWithRoundingHack(confirmedAmount, amount)
        }

        if (description != null) {
            assertThat(confirmedDescription).isEqualTo(description)
        }

        if (fee != null) {
            // Since we don't know if confirmedFee is in "primaryAmount" (e.g BTC) or
            // "secondaryAmount" (e.g probably FIAT, primary currency) we check against both
            try {
                assertMoneyEqualsWithRoundingHack(confirmedFee, fee.primaryAmount)
            } catch (e: AssertionError) {
                try {
                    assertMoneyEqualsWithRoundingHack(confirmedFee, fee.secondaryAmount)
                } catch (e: AssertionError) {
                    throw AssertionError(
                        // Yeah, this is gross, but let's output all the debuggin data we can if
                        // tests fail because of this.
                        // TODO: abstract this a little if it becomes necessary elsewhere
                        String.format(
                            "\nExpecting:\n  <%s>\nto be close to:\n  <%s>\n" +
                                    "by less than <%s> but difference was <%s>.\n" +
                                    "(a difference of exactly <%s> being considered valid)\n" +
                                    "OR to be close to:\n  <%s>\n" +
                                    "by less than <%s> but difference was <%s>.%n" +
                                    "(a difference of exactly <%s> being considered valid)\n",
                            confirmedFee.toString(),
                            fee.primaryAmount.toString(),
                            moneyEqualsRoundingMarginBTC.toString(),
                            confirmedFee.number.toDouble() - fee.primaryAmount.number.toDouble(),
                            moneyEqualsRoundingMarginBTC.toString(),
                            fee.secondaryAmount.toString(),
                            moneyEqualsRoundingMarginFiat.toString(),
                            confirmedFee.number.toDouble() - fee.secondaryAmount.number.toDouble(),
                            moneyEqualsRoundingMarginFiat.toString()
                        ),
                        e
                    )
                }
            }
        }
    }

    private fun checkConfirmedData(
        invoice: LnInvoice,
        desc: String,
        debtType: DebtType = DebtType.NONE
    ) {

        checkStep(NewOperationStep.CONFIRM)

        // Ensure amounts in BTC (needed for checks using satoshis amounts)
        if (!newOpScreen.confirmedAmount.currency.isBtc()) {
            rotateAmountCurrencies()
        }

        assertThat(confirmedDescription).isNotEmpty()

        val total = confirmedAmount.add(confirmedFee)

        assertMoneyEqualsWithRoundingHack(total, confirmedTotal)

        val amount = BitcoinUtils.satoshisToBitcoins(invoice.amount.amountInSatoshis)
        assertMoneyEqualsWithRoundingHack(confirmedAmount, amount)

        assertThat(confirmedDescription).isEqualTo(desc)

        val confirmedFeeInSat = BitcoinUtils.bitcoinsToSatoshis(confirmedFee)

        // Let's check swap fee has a "reasonable amount" (e.g is in certain aprox range)
        if (debtType == DebtType.LEND) {
            assertThat(confirmedFeeInSat < 2) // No on-chain swap => no on-chain fees, only ln fee

        } else { // Normal & Collect swaps
            assertThat(confirmedFeeInSat > 220) // On-chain (226 * 1 sat/vbyte) + sweep + ln fee
        }
    }

    private fun waitForResolveOperationUri() {
        device.waitForIdle()

        // Let's wait until RESOLVING step is over
        waitUntilGone(R.id.new_operation_resolving)

        assertThat(detectStep()).isNotEqualTo(NewOperationStep.RESOLVING)
    }

    private fun checkStep(step: NewOperationStep) =
        assertThat(detectStep()).isEqualTo(step)

    private fun detectStep() =
        when {
            id(R.id.new_operation_resolving).exists() -> NewOperationStep.RESOLVING
            id(R.id.muun_amount).exists()             -> NewOperationStep.ENTER_AMOUNT
            id(R.id.muun_note_input).exists()         -> NewOperationStep.ENTER_DESCRIPTION
            id(R.id.total_amount).exists()            -> NewOperationStep.CONFIRM

            else ->
                throw RuntimeException("Cannot detect new operation step")
        }
}