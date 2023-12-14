package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.utils.SystemCommand
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import io.muun.apollo.utils.WithMuunInstrumentationHelpers.Companion.balanceNotEqualsErrorMessage
import io.muun.common.utils.Preconditions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import javax.money.MonetaryAmount
import kotlin.random.Random

class HomeScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    val balanceInBtc get() = id(R.id.balance_main_currency_amount).text.toBtcMoney()

    fun waitUntilVisible() {
        id(R.id.home_balance_view).await(10000)
    }

    fun waitForWelcomeDialog(): Boolean {
        // BIG timeout since sometimes (especially 1st test in suite) initial sync take looooooooong
        return label(R.string.welcome_to_muun).waitForExists(25000)
    }

    fun waitUntilBalanceEquals(expectedBalance: MonetaryAmount) {
        val result = pullNotificationsUntil(20000) {
            balanceEquals(expectedBalance)
        }

        assertThat(result)
            .withFailMessage("$balanceNotEqualsErrorMessage:$expectedBalance")
            .isTrue
    }

    fun checkBalanceCloseTo(expectedBalance: MonetaryAmount) {

        waitUntilVisible()

        val actualBalance = balanceInBtc

        assertThat(actualBalance.currency).isEqualTo(expectedBalance.currency)

        assertThat(actualBalance.number.toDouble())
            .isCloseTo(expectedBalance.number.toDouble(), within(0.00001))
    }

    fun goToReceive() {
        pressMuunButtonAndWaitForNewWindow(R.id.home_receive_button)
    }

    fun goToSettings() {
        id(R.id.settings_fragment).click()
    }

    fun goToOperationDetail(description: String, isPending: Boolean = false) {
        openOperationHistory()

        openOperationDetail(description, isPending)
    }

    fun goToOperationDetail(index: Int) {
        openOperationHistory()

        val listUiObject = id(R.id.home_operations_recycler_operation_list)

        // NOTE: unless you combine .instance() with another selector method (like .clickable()),
        // it doesn't work as expected :'(
        listUiObject.getChild(UiSelector().clickable(true).instance(index)).click()
    }

    private fun openOperationHistory() {
        id(R.id.chevron).clickAndWaitForNewWindow()

        checkScreenShows(R.string.home_operations_list_title)

        // We've observe some pretty common flakiness for clicks after opening opHistory. Specially
        // nasty since views ARE found by uiAutomator and click() returns ok but the view doesn't
        // get clicked. We believe it has to do with the activity transition and the fact that the
        // view is already visible (probably part of the view hierarchy) but the framework may not
        // click on the exact area where the view is (after all its moving, maybe the clickable area
        // is also moving, or its always at the final place where the view ends after animation).
        // Sooooo, we wait for a bit :). This right here kills the flakiness we were having.
        busyWait(1500)
    }

    private fun openOperationDetail(description: String, isPending: Boolean) {

        try {
            clickOperation(isPending, description)

        } catch (ex: UiObjectNotFoundException) {
            // This probably means we missed an opUpdate notif. Let's try again, flakiness is ugly.

            // Wait 1.5 secs...
            busyWait(1500)

            // Try again
            clickOperation(isPending, description)
        }
    }

    private fun clickOperation(isPending: Boolean, description: String) {
        val pendingText = context.getString(R.string.operation_pending) + ": "

        try {
            label("${if (isPending) pendingText else ""}$description").click()

        } catch (e: UiObjectNotFoundException) {
            val id = Random.nextInt()
            println("clickOperation: isPending: $isPending, desc: $description")
            println("clickOperation: dumpview id: $id")
            SystemCommand.dumpView(id)
            throw e
        }
    }

    fun goToOperationDetailContaining(description: String) {
        openOperationHistory()

        labelWith(description).click()
    }

    fun goToSend() {
        pressMuunButtonAndWaitForNewWindow(R.id.home_send_button)
    }

    fun goToP2PSetup() {
        goToSend()

        // We're using Espresso for this, we hit a roadblock with UiAutomator (can't click spans)
        onView(withId(R.id.muun_empty_screen_text))
            .perform(clickClickableSpan(R.string.contact_list_empty_clickable_span))
    }

    fun goToSecurityCenter() {
        id(R.id.security_center_fragment).click()
    }

    private fun balanceEquals(expectedBalance: MonetaryAmount): Boolean {
        Preconditions.checkArgument(expectedBalance.isBtc())

        val maybeBalanceView = maybeViewId(R.id.balance_main_currency_amount)

        @Suppress("FoldInitializerAndIfToElvis") // Prefer if for readability (familiarity)
        if (maybeBalanceView == null) {
            return false
        }

        val balance = maybeBalanceView.text
        println("balanceEquals: actual: $balance, expected: $expectedBalance, short: ${expectedBalance.toShortText()}")

        return maybeBalanceView
            .wait(Until.textContains(expectedBalance.toShortText()), 1000)
    }
}