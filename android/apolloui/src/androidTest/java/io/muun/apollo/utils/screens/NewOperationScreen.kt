package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.new_operation.NewOperationStep
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
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
        id(R.id.muun_next_step_button).waitForExists(15000)
    }

    fun fillForm(amount: MonetaryAmount?, description: String?) {
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
        confirmUseAllFunds()

        if (description != null) {
            editDescription(description)
            goNext()
        }

        checkConfirmedData(description=description)

        return confirmedAmount
    }

    fun fillForm(invoice: LnInvoice,
                 description: String? = null,
                 debtType: DebtType = DebtType.NONE) {

        waitForResolveOperationUri()

        if (description != null) {
            editDescription(description)
            goNext()
        }

        checkConfirmedData(invoice, description ?:invoice.description, debtType)
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

    fun checkConfirmedData(amount: MonetaryAmount? = null,
                           description: String? = null,
                           fee: MonetaryAmount? = null) {

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
            assertMoneyEqualsWithRoundingHack(confirmedFee, fee)
        }
    }

    private fun checkConfirmedData(invoice: LnInvoice,
                                   desc: String,
                                   debtType: DebtType = DebtType.NONE) {

        checkStep(NewOperationStep.CONFIRM)

        // Ensure amounts in BTC (needed for checks using satoshis amounts)
        if (!MoneyHelper.isBtc(newOpScreen.confirmedAmount.currency)) {
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