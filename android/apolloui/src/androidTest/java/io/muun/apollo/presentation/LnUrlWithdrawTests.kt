package io.muun.apollo.presentation

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.debug.LappClient
import io.muun.apollo.utils.MuunTexts
import io.muun.common.utils.BitcoinUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class LnUrlWithdrawTests : BaseInstrumentationTest() {

    @Test
    fun test_01_a_user_can_withdraw_using_lnurl_via_receive() {
        autoFlows.signUp()

        // For now the withdraw amount is fixed
        val amountToWithdraw = BitcoinUtils.satoshisToBitcoins(3000)
        val balanceBefore = homeScreen.balanceInBtc

        autoFlows.lnUrlWithdrawViaReceive()

        // We should be at home by now
        homeScreen.checkBalanceCloseTo(balanceBefore.add(amountToWithdraw))
    }

    @Test
    fun test_02_a_user_can_withdraw_using_lnurl_via_send() {
        autoFlows.signUp()

        // For now the withdraw amount is fixed
        val amountToWithdraw = BitcoinUtils.satoshisToBitcoins(3000)
        val balanceBefore = homeScreen.balanceInBtc

        autoFlows.lnUrlWithdrawViaSend()

        // We should be at home by now
        homeScreen.checkBalanceCloseTo(balanceBefore.add(amountToWithdraw))
    }

    @Test
    fun test_03_a_user_can_withdraw_using_lnurl_when_taking_too_long() {
        autoFlows.signUp()

        // For now the withdraw amount is fixed
        val amountToWithdraw = BitcoinUtils.satoshisToBitcoins(3000)
        val balanceBefore = homeScreen.balanceInBtc

        autoFlows.lnUrlWithdrawViaSend(LappClient.LnUrlVariant.SLOW)

        // We should be at home by now
        homeScreen.checkBalanceCloseTo(balanceBefore)  // balance should not change immediately
        homeScreen.waitUntilBalanceEquals(balanceBefore.add(amountToWithdraw))
    }

    @Test
    fun test_04_a_user_can_report_a_reportable_lnurl_error() {
        autoFlows.signUp()

        autoFlows.startLnUrlWithdrawViaSend(LappClient.LnUrlVariant.FAILS)

        label(R.string.error_lnurl_unknown_title).assertExists()

        muunButton(R.id.primary_button).waitForExists()
            .textEquals(MuunTexts.normalize(R.string.send_report))
            .press()
    }

    @Test
    fun test_05_a_user_can_retry_a_retryable_lnurl_error() {
        autoFlows.signUp()

        autoFlows.startLnUrlWithdrawViaSend(LappClient.LnUrlVariant.UNRESPONSIVE)

        // Let's wait for unresponsive service error (+15 secs)
        SystemClock.sleep(16_000)

        muunButton(R.id.primary_button).waitForExists()
            .textEquals(MuunTexts.normalize(R.string.retry))
            .press()

        muunButton(R.id.primary_button).doesntExist()

        labelWith(R.string.contacting).assertExists()

        // Let's wait for unresponsive service error (+15 secs)
        SystemClock.sleep(16_000)

        muunButton(R.id.primary_button).waitForExists()
            .textEquals(MuunTexts.normalize(R.string.retry))
            .press()

        muunButton(R.id.primary_button).doesntExist()

        labelWith(R.string.contacting).assertExists()

        // Unfortunately this is as far as we go, we only test that the withdraw can be retried
    }

    @Test
    fun test_06_handle_no_balance_to_withdraw_using_lnurl() {
        autoFlows.signUp()

        val balanceBefore = homeScreen.balanceInBtc

        autoFlows.startLnUrlWithdrawViaSend(LappClient.LnUrlVariant.NO_BALANCE)

        label(R.string.error_lnurl_no_balance_title).assertExists()

        muunButton(R.id.primary_button).waitForExists()
            .textEquals(MuunTexts.normalize(R.string.error_op_action))
            .press()

        // We should be at home by now
        homeScreen.checkBalanceCloseTo(balanceBefore)  // balance should not change
    }

    @Test
    fun test_07_handle_expired_lnurl_error() {
        autoFlows.signUp()

        val balanceBefore = homeScreen.balanceInBtc

        autoFlows.startLnUrlWithdrawViaSend(LappClient.LnUrlVariant.EXPIRED_LNURL)

        label(R.string.error_lnurl_expired_title).assertExists()

        muunButton(R.id.primary_button).waitForExists()
            .textEquals(MuunTexts.normalize(R.string.error_op_action))
            .press()

        // We should be at home by now
        homeScreen.checkBalanceCloseTo(balanceBefore)  // balance should not change
    }

    @Test
    fun test_08_handle_no_route_lnurl_error() {
        autoFlows.signUp()

        val balanceBefore = homeScreen.balanceInBtc

        autoFlows.startLnUrlWithdrawViaSend(LappClient.LnUrlVariant.NO_ROUTE)

        label(R.string.error_lnurl_no_route_title).assertExists()

        muunButton(R.id.primary_button).waitForExists()
            .textEquals(MuunTexts.normalize(R.string.error_op_action))
            .press()

        // We should be at home by now
        homeScreen.checkBalanceCloseTo(balanceBefore)  // balance should not change
    }

    @Test
    fun test_09_handle_invalid_lnurl_tag_error() {
        autoFlows.signUp()

        val balanceBefore = homeScreen.balanceInBtc

        autoFlows.startLnUrlWithdrawViaSend(LappClient.LnUrlVariant.WRONT_TAG)

        label(R.string.error_invalid_lnurl_tag_title).assertExists()

        muunButton(R.id.primary_button).waitForExists()
            .textEquals(MuunTexts.normalize(R.string.error_op_action))
            .press()

        // We should be at home by now
        homeScreen.checkBalanceCloseTo(balanceBefore)  // balance should not change
    }
}