package io.muun.apollo.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.muun.apollo.R
import io.muun.apollo.data.debug.LappClient
import io.muun.apollo.data.external.Globals
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.utils.RandomUser
import io.muun.common.model.DebtType
import io.muun.common.utils.BitcoinUtils
import io.muun.common.utils.LnInvoice
import org.assertj.core.api.Assertions.assertThat
import org.javamoney.moneta.Money
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
open class NewOperationTests : BaseInstrumentationTest() {

    @Test
    fun test_01_a_user_can_receive_money_from_network() {
        autoFlows.signUp()

        autoFlows.receiveMoneyFromNetwork(Money.of(0.0102, "BTC"))
    }

    @Test
    fun test_02_user_can_send_btc_to_address() {
        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.02, "BTC"))

        val moneyToSend = Money.of(0.01, "BTC")
        val receivingAddress = "2N2y9wGHh7AfqwQ8dk5cQfhjvEAAq6xhjb6"
        val description = "This is a note " + System.currentTimeMillis()

        autoFlows.sendToAddressFromClipboard(moneyToSend, receivingAddress, description)
        autoFlows.settleOperation(description)
    }

    @Test
    @Ignore("Need new way of testing since setupP2P was disabled")
    fun test_03_user_can_send_btc_to_contact() {
        val contact = RandomUser()

        autoFlows.signUpUserWithExistingUserAsContact(contact)

        // Send money to contact:
        autoFlows.receiveMoneyFromNetwork(Money.of(0.02, "BTC"))
        val moneyToSend = Money.of(0.01, "BTC")
        val description = "This is a note " + System.currentTimeMillis()

        autoFlows.newOperation(moneyToSend, description) {

            homeScreen.goToSend()
            label(contact.fullName).click()
        }

        autoFlows.settleOperation(description)
    }

    @Test
    fun test_04_user_can_change_fee_in_small_tx() {
        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        val moneyToSend = Money.of(0.00001, "BTC")
        val receivingAddress = "2N2y9wGHh7AfqwQ8dk5cQfhjvEAAq6xhjb6"
        val description = "This is a note " + System.currentTimeMillis()

        autoFlows.startOperationFromClipboardTo(receivingAddress)

        newOpScreen.fillForm(moneyToSend, description)

        autoFlows.tryAllFeesAndExit()
    }

    @Test
    fun test_05_user_can_change_fee_in_use_all_funds_with_large_balance() {
        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        val receivingAddress = "2N2y9wGHh7AfqwQ8dk5cQfhjvEAAq6xhjb6"
        val description = "This is a note " + System.currentTimeMillis()

        autoFlows.startOperationFromClipboardTo(receivingAddress)

        newOpScreen.fillFormUsingAllFunds(description)

        autoFlows.tryAllFeesAndExit()
    }

    @Test
    fun test_06_user_can_pay_a_0_conf_ln_invoice_with_a_submarine_swap_without_debt() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // This amount is "brittle/fickle" as it depends on service configs
        // Must be greater than MAX_DEBT_PER_USER but lower than AMOUNT_FOR_ZERO_CONFS_IN_USD
        val amountThatWillTriggerA0ConfSwapWithoutDebt = 50000
        autoFlows.newSubmarineSwap(amountThatWillTriggerA0ConfSwapWithoutDebt)

    }

    @Test
    fun test_07_user_can_pay_a_1_conf_ln_invoice_with_a_submarine_swap() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // This amount is "brittle/fickle" as it depends on service configs and btc price
        val amountThatWillTriggerA1ConfSwap = 400_000 // Should be > AMOUNT_FOR_ZERO_CONFS_IN_USD
        autoFlows.newSubmarineSwap(amountThatWillTriggerA1ConfSwap)
    }

    @Ignore
    @Test
    fun test_08_user_can_pay_using_refunded_funds_after_a_failed_swap() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        val lnInvoice = LappClient().getLnInvoice(160_000)
        val description = "This is a note " + System.currentTimeMillis()

        autoFlows.newSubmarineSwap(lnInvoice, description) {
            autoFlows.startOperationFromClipboardTo(lnInvoice.original)
        }

        // TODO find a way to stop lnd or close all channels

        LappClient().generateBlocks(1)

        autoFlows.checkSubmarineSwapFail()
    }

    @Test
    fun test_11_user_can_pay_a_submarine_swap_using_debt() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerALendSwap = 10_000 // Should be < MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap, DebtType.LEND)
    }

    @Test
    fun test_12_user_can_pay_while_we_collect_debt() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // Let's lend money to user, via a LEND SWAP

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerALendSwap = 10_000 // Should be < MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap, DebtType.LEND)

        // Let's collect money from user, via a COLLECT SWAP

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerACollectSwap = 20_000 // To be sure that go over MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerACollectSwap, DebtType.COLLECT)
    }

    @Test
    fun test_13_user_can_default_debt() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // Let's lend money to user, via a LEND SWAP

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerALendSwap = 200 // Should be < MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap, DebtType.LEND)

        // Oops. We need to default money from the user debt. Default TX

        val description = "This is a note " + System.currentTimeMillis()

        autoFlows.spendAllFunds(description)
        autoFlows.settleOperation(description)
    }

    @Test
    fun test_14_user_can_receive_small_amount_via_LN_using_amountless_invoice() {
        autoFlows.signUp()

        // 1. Generate a small amount of debt
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerALendSwap = 200 // Should be < MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap, DebtType.LEND)

        // 2. Let's receive via LN using that debt

        // Receive low amount (full debt) 100 sats
        autoFlows.receiveMoneyFromLNWithAmountLessInvoice(100)
    }

    @Test
    fun test_15_user_can_receive_big_amount_via_LN_using_amountless_invoice() {
        autoFlows.signUp()

        // Receive big amount (> debt limit, htlc + fullfilment tx) 100k sats
        autoFlows.receiveMoneyFromLNWithAmountLessInvoice(100_000)
    }

    @Test
    fun test_16_user_can_receive_via_LN_using_amountless_invoice_without_turbo_channels() {
        autoFlows.signUp()

        autoFlows.toggleTurboChannels()

        // Receive disabling turbo channels 100k sats
        val amountInSats: Long = 100_000
        autoFlows.receiveMoneyFromLNWithAmountLessInvoice(amountInSats, false)

        LappClient().generateBlocks(1)

        val amount = BitcoinUtils.satoshisToBitcoins(amountInSats)
        autoFlows.checkOperationDetails(amount, statusPending = false) {
            homeScreen.goToOperationDetail(0)
        }
    }

    /**
     * Premature spend: receive via LN with 1 conf (big amount + turbo channels disabled) and spend
     * the funds before confirmation.
     */
    @Test
    fun test_17_user_can_do_a_premature_spend_using_amountless_invoice() {
        autoFlows.signUp()

        autoFlows.toggleTurboChannels()

        // Receive disabling turbo channels 100k sats
        val amountInSats: Long = 100_000
        autoFlows.receiveMoneyFromLNWithAmountLessInvoice(amountInSats, false)

        // Premature spend (big amount and spend before 1-conf) 100k sats
        val description = "Some description"
        autoFlows.spendAllFunds(description)
        autoFlows.settleOperation(description)

        LappClient().generateBlocks(1)

        val amount = BitcoinUtils.satoshisToBitcoins(amountInSats)
        autoFlows.checkOperationDetails(amount, statusPending = false) {
            homeScreen.goToOperationDetail(1)
        }
    }

    @Test
    fun test_18_user_can_receive_small_amount_via_LN_using_invoice_with_amount() {
        autoFlows.signUp()

        // 1. Generate a small amount of debt
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerALendSwap = 200 // Should be < MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap, DebtType.LEND)

        // 2. Let's receive via LN using that debt

        // Receive low amount (full debt) 100 sats
        autoFlows.receiveMoneyFromLNWithInvoiceWithAmount(100)
    }

    @Test
    fun test_19_user_can_receive_big_amount_via_LN_using_invoice_with_amount() {
        autoFlows.signUp()

        // Receive big amount (> debt limit, htlc + fullfilment tx) 100k sats
        autoFlows.receiveMoneyFromLNWithInvoiceWithAmount(100_000)
    }

    @Test
    fun test_20_user_can_receive_via_LN_using_invoice_with_amount_without_turbo_channels() {
        autoFlows.signUp()

        autoFlows.toggleTurboChannels()

        // Receive disabling turbo channels 100k sats
        val amountInSats: Long = 100_000
        autoFlows.receiveMoneyFromLNWithInvoiceWithAmount(amountInSats, false)

        LappClient().generateBlocks(1)

        val amount = BitcoinUtils.satoshisToBitcoins(amountInSats)
        autoFlows.checkOperationDetails(amount, statusPending = false) {
            homeScreen.goToOperationDetail(0)
        }
    }

    /**
     * Premature spend: receive via LN with 1 conf (big amount + turbo channels disabled) and spend
     * the funds before confirmation.
     */
    @Test
    fun test_21_user_can_do_a_premature_spend_using_invoice_with_amount() {
        autoFlows.signUp()

        autoFlows.toggleTurboChannels()

        // Receive disabling turbo channels 100k sats
        val amountInSats: Long = 100_000
        autoFlows.receiveMoneyFromLNWithInvoiceWithAmount(amountInSats, false)

        // Premature spend (big amount and spend before 1-conf) 100k sats
        val description = "Some description"
        autoFlows.spendAllFunds(description)
        autoFlows.settleOperation(description)

        LappClient().generateBlocks(1)

        val amount = BitcoinUtils.satoshisToBitcoins(amountInSats)
        autoFlows.checkOperationDetails(amount, statusPending = false) {
            homeScreen.goToOperationDetail(1)
        }
    }

    @Test
    fun test_22_user_HAS_to_change_fee_to_pay_due_to_outputAmout_lower_than_dust() {
        autoFlows.signUp()

        // Receive an incoming swap of 985, generating an outputAmount and debt of aprox 5500 sats
        val amountInSats: Long = 985
        autoFlows.receiveMoneyFromLNWithInvoiceWithAmount(amountInSats)

        val receivingAddress = "2N2y9wGHh7AfqwQ8dk5cQfhjvEAAq6xhjb6"
        val note = "This is a note"

        // Try to spend AllFunds, but fail: feeNeedsChange. OutputAmount is lower than dust

        val balanceBefore = homeScreen.balanceInBtc
        autoFlows.startOperationFromClipboardTo(receivingAddress)

        newOpScreen.waitUntilVisible()
        newOpScreen.fillFormUsingAllFunds(note)

        // Ensure amounts in BTC (needed for post submit checks in balance and opDetail)
        if (!newOpScreen.confirmedAmount.currency.isBtc()) {
            newOpScreen.rotateAmountCurrencies()
        }

        // Keep these to check later:
        val description = newOpScreen.confirmedDescription
        var total = newOpScreen.confirmedTotal

        assertThat(total.number.toDouble()).isGreaterThanOrEqualTo(balanceBefore.number.toDouble())
        newOpScreen.assertSubmitIsDisabled()

        newOpScreen.goToEditFee()
        recomFeeScreen.goToManualFee()
        val optionManual = manualFeeScreen.editFeeRate(1.0)
        manualFeeScreen.confirmFeeRate()

        Thread.sleep(2000)
        newOpScreen.waitUntilVisible()

        var amount = newOpScreen.confirmedAmount
        var fee = newOpScreen.confirmedFee
        total = newOpScreen.confirmedTotal

        // Ensure amounts in BTC (needed for post submit checks in balance and opDetail)
        if (!newOpScreen.confirmedAmount.currency.isBtc()) {
            // Yeap this apparently is necessary. And YES, IT MUST happen here, if check is before
            // var amount definition, it inexplicably fails
            newOpScreen.rotateAmountCurrencies()

            amount = newOpScreen.confirmedAmount
            fee = newOpScreen.confirmedFee
            total = newOpScreen.confirmedTotal
        }

        newOpScreen.checkConfirmedData(amount, description, optionManual)

        newOpScreen.submit()

        homeScreen.waitUntilBalanceCloseTo(balanceBefore.subtract(total))

        autoFlows.checkOperationDetails(amount, description, fee) {
            homeScreen.goToOperationDetail(description, isPending = true)
        }
    }

    @Test
    fun test_23_user_can_make_cyclic_payment() {
        autoFlows.signUp()

        autoFlows.receiveMoneyFromNetwork(Money.of(0.0102, "BTC"))

        userCanMakeAOnchainCyclePayment()

        userCanNotMakeAOffChainCyclePayment()
    }

    private fun userCanMakeAOnchainCyclePayment() {
        val receivingAddress = autoFlows.getOwnAddress()
        val moneyToSend = Money.of(0.0001, "BTC")
        val description = "This is a note " + System.currentTimeMillis()

        autoFlows.newOperation(moneyToSend, description) {
            autoFlows.startOperationManualInputTo(receivingAddress)
        }
        autoFlows.settleOperation(description)
    }

    private fun userCanNotMakeAOffChainCyclePayment() {
        val ownInvoice = autoFlows.getOwnInvoice()
        val lnInvoice = LnInvoice.decode(Globals.INSTANCE.network, ownInvoice)

        autoFlows.startOperationManualInputTo(lnInvoice.original)

        // We don't allow cyclic ln payments yet
        label(R.string.error_op_cyclical_swap_title).assertExists()
        label(R.string.error_op_cyclical_swap_desc).assertExists()
    }
}