package io.muun.apollo.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.muun.apollo.presentation.ui.debug.LappClient
import io.muun.common.utils.BitcoinUtils
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
        // TODO ?
    }

    @Test
    fun test_04_user_can_change_fee_in_small_tx() {
        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        val moneyToSend = Money.of(10, "USD")
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
        // Must be greate than MAX_DEBT_PER_USER but lower than AMOUNT_FOR_ZERO_CONFS_IN_USD
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
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap)
    }

    @Test
    fun test_12_user_can_pay_while_we_collect_debt() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // Let's lend money to user, via a LEND SWAP

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerALendSwap = 10_000 // Should be < MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap)

        // Let's collect money from user, via a COLLECT SWAP

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerACollectSwap = 20_000 // To be sure that go over MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerACollectSwap)
    }

    @Test
    fun test_13_user_can_default_debt() {

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.1, "BTC"))

        // Let's lend money to user, via a LEND SWAP

        // This amount is "brittle/fickle" as it depends on service configs
        val amountThatWillTriggerALendSwap = 200 // Should be < MAX_USER_DEBT
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap)

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
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap)

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
        autoFlows.newSubmarineSwap(amountThatWillTriggerALendSwap)

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
}