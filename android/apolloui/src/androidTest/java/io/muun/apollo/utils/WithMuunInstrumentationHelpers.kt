package io.muun.apollo.utils

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.uiautomator.*
import io.muun.apollo.BuildConfig
import io.muun.apollo.R
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.debug.LappClient
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.utils.screens.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.javamoney.moneta.Money
import java.text.NumberFormat
import java.text.ParseException
import java.text.ParsePosition
import java.util.*
import javax.money.MonetaryAmount

/**
 * Requires: an implementation of getDevice: UiDevice.
 * Provides: convenience methods for finding and interacting with UiObjects
 * Note: you'll notice that we use a mix of UiObject and UiObject2, the reason behind is long lost
 * in the weird rivers of time. I can only offer you this:
 * https://stackoverflow.com/q/40881680/901465 (GREAT post on difference between these two)
 */
interface WithMuunInstrumentationHelpers : WithMuunEspressoHelpers {

    companion object {
        const val resourcePath = BuildConfig.APPLICATION_ID + ":id"

        const val moneyEqualsRoundingMarginBTC = 0.00000001
        const val moneyEqualsRoundingMarginFiat = 0.01

        const val balanceNotEqualsErrorMessage = "BalanceNotEqualsTo"
    }

    val device: UiDevice

    val locale
        get() =
            context.locale()

    // UI Components:

    val toolbar
        get() =
            MuunToolbar(device, context)

    val dialog
        get() =
            MuunDialog(device, context)

    val uriPaster
        get() =
            UriPaster(device, context)

    // Screens:

    val newOpScreen
        get() =
            NewOperationScreen(device, context)

    val homeScreen
        get() =
            HomeScreen(device, context)

    val settingsScreen
        get() =
            SettingsScreen(device, context)

    val p2pScreen get() =
        SetupP2PScreen(device, context)

    val recoveryCodeScreen
        get() =
            RecoveryCodeSetupScreen(device, context)

    val emergencyKitSetupScreen
        get() =
            EmergencyKitSetupScreen(device, context)

    val changePasswordScreen
        get() =
            ChangePasswordScreen(device, context)

    val signInScreen
        get() =
            SignInScreen(device, context)

    val opDetailScreen
        get() =
            OperationDetailScreen(device, context)

    val receiveScreen
        get() =
            ReceiveScreen(device, context)

    val recomFeeScreen
        get() =
            RecommendedFeeScreen(device, context)

    val manualFeeScreen
        get() =
            ManualFeeScreen(device, context)

    val securityCenterScreen
        get() =
            SecurityCenterScreen(device, context)

    val emailPasswordScreen
        get() =
            EmailPasswordSetupScreen(device, context)

    // TODO this interface is starting to get BIG, we may need to start separating the functionality
    // contained here into multiple interfaces
    fun grantPermission(permission: String) {
        SystemCommand.grantPermission(context.packageName, permission)
    }

    /**
     * Assumes you are in home screen.
     */
    fun generateBlocksAndWaitForUpdate(blocks: Int) {
        LappClient().generateBlocks(blocks)

        tryPullNotifications()
    }

    /**
     * Utility method to wait for a notification to arrive for a certain amount of time, while
     * actively trying to pull notifications. Notice the way its implemented, we don't try enforce
     * an accurate timeout, but instead guarantee that the exitCondition and the process of
     * pulling notifications are not interrupted or left incomplete.
     */
    fun pullNotificationsUntil(timeoutInMillis: Int, exitCondition: () -> Boolean): Boolean {
        return doUntil(timeoutInMillis, exitCondition) {
            tryPullNotifications()
        }
    }

    /**
     * Utility method to wait for a certain amount of time, while performing certain action.
     * Notice the way its implemented, we don't try enforce an accurate timeout, but instead
     * guarantee that the exitCondition and the repeated action are not interrupted or
     * left incomplete.
     */
    fun doUntil(timeoutInMillis: Int, exitCondition: () -> Boolean, action: () -> Unit): Boolean {
        val startTime = System.currentTimeMillis()

        while (true) {

            val result = exitCondition()

            val spentTime = System.currentTimeMillis() - startTime

            if (result || spentTime >= timeoutInMillis) {
                return result
            }

            action()
        }
    }

    /**
     * Assumes you are in home screen.
     */
    fun tryPullNotifications() {
        // Let's go out of the home screen to trigger pull notifications and increase odds of
        // receiving operationUpdate notification
        homeScreen.goToSend()

        // Wait for a couple secs
        id(R.id.home_balance_view).waitForExists(3000)

        // Go back home and pull notifications
        device.pressBack()

        // Wait make sure we reached home
        assertThat(id(R.id.home_balance_view).waitForExists(3000)).isTrue()
    }

    /** Obtain a view matching a normalized string.
     *  <p>
     *  NOTE: Some Android versions behave differently when text UISelector is used. Some (>23)
     *  target what is actually rendered on screen while others target the string resource.
     *  This introduces differences when flags like textAllCaps or ellipsize are used.
     */
    fun normalizedLabel(@StringRes stringResId: Int): UiObject = label(MuunTexts.normalize(stringResId))

    /** Obtain a view matching a string. */
    fun label(@StringRes stringResId: Int): UiObject = label(context.getString(stringResId))

    /** Obtain a view matching a string. */
    fun label(text: String): UiObject = device.findObject(UiSelector().text(text))

    /** Obtain a view matching a string, that must be contained somewhere. */
    fun labelWith(text: String): UiObject = device.findObject(UiSelector().textContains(text))

    /** Obtain a view matching a string, that must be contained somewhere. */
    fun labelWith(@StringRes stringResId: Int): UiObject = device.findObject(UiSelector().textContains(context.getString(stringResId)))

    /** Obtain a view matching a string, found in the description. */
    fun desc(@StringRes stringResId: Int): UiObject = device.findObject(UiSelector().description(context.getString(stringResId)))

    /** Obtain a view (if it exists) matching by id. */
    fun maybeViewId(@IdRes id: Int): UiObject2 = device.findObject(By.res(resourceName(id)))

    /**
     *  Obtain a view (waiting for it to exist) matching by id resource.
     *  Note: whenever possible we should try to avoid using this generic targetting method and
     *  instead use more specific ones (targetting specific components or classes) like
     *  button(), detailItem(), input(), etc...
     */
    fun id(@IdRes id: Int): UiObject = device.findObject(idSelector(id))

    /** Obtain a view (waiting for it to exist) matching by id resource name. */
    fun id(id: String): UiObject = device.findObject(idSelector(id))

    /** Obtain a MuunButton, matching by id resource name. */
    fun button(@IdRes id: Int): UiObject = id(id).getChild(idSelector(R.id.muun_button_button))

    /** Obtain a MuunButton as a domain object, matching by id resource name. */
    fun muunButton(@IdRes id: Int): MuunButton =
            MuunButton(device, context, button(id))

    /** Obtain a MuunDetailItem, matching by id resource name. */
    fun detailItem(@IdRes id: Int): UiObject = device.findObject(idSelector(id))

    /** Obtain a MuunDetailItem's content, matching by id resource name. */
    fun detailItemContent(@IdRes id: Int): UiObject =
            detailItem(id).getChild(idSelector(R.id.operation_detail_item_text_content))

    fun maybeDetailItemTitle(@IdRes id: Int): UiObject2 =
            maybeViewId(id).findObject(By.res(resourceName(R.id.operation_detail_item_text_title)))

    /** Obtain a MuunDetailItem's title, matching by id resource name. */
    fun detailItemTitle(@IdRes id: Int): UiObject =
            detailItem(id).getChild(idSelector(R.id.operation_detail_item_text_title))

    /** Obtain a MuunDetailItem's image, matching by id resource name. */
    fun detailItemImage(@IdRes id: Int): UiObject =
            detailItem(id).getChild(idSelector(R.id.operation_detail_item_icon))


    /** Obtain a MuunSettingsItem's title, matching by id resource name. */
    fun settingsItemTitle(@IdRes id: Int): UiObject =
            detailItem(id).getChild(idSelector(R.id.setting_item_label))

    /** Obtain a MuunDetailItem's content, matching by id resource name. */
    fun settingsItemContent(@IdRes id: Int): UiObject =
            detailItem(id).getChild(idSelector(R.id.setting_item_description))

    /** Obtain a MuunTextInput, matching by id resource name. */
    fun input(@IdRes id: Int): UiObject =
            id(id).getChild(idSelector(R.id.muun_text_input_edit_text))

    fun inputError(@IdRes id: Int): UiObject2 =
            maybeViewId(id).wait(Until.findObject(ByShortName("textinput_error")), 30000)

    /** Obtain a Muun's empty screen action button, matching by id resource name. */
    fun emptyScreenButton(@IdRes id: Int): UiObject =
            id(id).getChild(idSelector(R.id.muun_button_button))

    fun androidPackageInstaller(id: String): UiObject =
            device.findObject(fullId("com.android.packageinstaller:id/$id"))

    /**
     * Scroll to find or do something, but first try to find/do it without scrolling, to avoid
     * wasting time (e.g test can run faster).
     */
    fun scrollTo(action: () -> Unit) {

        try {
            action()

        } catch (e: UiObjectNotFoundException) {
            scrollDown()
            action()
        }
    }

    /** Scroll down until a visible item that matches the {@link UiObject} is found. */
    fun scrollToFind(uiObject: UiObject) {

        try {
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(uiObject)
        } catch (e: UiObjectNotFoundException) {
            // if there's no scrollable item, don't scroll but let's continue if we can
        }
    }

    fun waitUntilGone(@IdRes id: Int) =
            device.wait(Until.gone(By.res(resourceName(id))), 4000) ?: false

    /**
     * Press the BACK key until a view with a given ID exists on screen, no more than `limit`
     * times, waiting `waitMs` for the view to appear before each press.
     */
    fun backUntilExists(@IdRes id: Int, limit: Int = 5, waitMs: Long = 1000) {
        var backPresses = 0

        while (backPresses < limit && !id(id).waitForExists(waitMs)) {
            device.pressBack()
            backPresses++
        }

        if (backPresses == limit) {
            throw UiObjectNotFoundException("After $limit back presses, $id wasn't found")
        }
    }

    fun sleep() =
            sleep(10)

    fun sleep(seconds: Long) =
            Thread.sleep(seconds * 1000)

    fun assertMoneyEqualsWithRoundingHack(actual: MonetaryAmount, expected: MonetaryAmount) {
        assertThat(actual.currency == expected.currency)

        val margin = if (actual.currency.currencyCode == "BTC") {
            moneyEqualsRoundingMarginBTC
        } else {
            moneyEqualsRoundingMarginFiat
        }

        assertThat(actual.number.toDouble()).isCloseTo(expected.number.toDouble(), within(margin))
    }

    /**
     * Check MuunButton is enabled and press/click it.
     */
    fun pressMuunButton(@IdRes id: Int) =
            button(id).assertEnabledAndClick()

    /**
     * Check MuunButton is enabled and press/click it, waiting for next activity, dialog, etc..
     */
    fun pressMuunButtonAndWaitForNewWindow(@IdRes id: Int) {
        val buttonObject = button(id)

        assertThat(buttonObject.isEnabled).isTrue()

        buttonObject.clickAndWaitForNewWindow()
    }

    fun checkInputError(@IdRes id: Int, @StringRes expectedErrorId: Int) {
        assertThat(inputError(id).text).isEqualTo(context.getString((expectedErrorId)))
    }

    fun checkScreenShows(text: String) {
        assert(labelWith(text).waitForExists(2000))
    }

    fun checkScreenShows(@StringRes stringResId: Int) {
        assert(labelWith(stringResId).waitForExists(2000))
    }

    private fun idSelector(@IdRes id: Int): UiSelector = UiSelector().resourceId(resourceName(id))

    private fun idSelector(id: String): UiSelector = fullId("$resourcePath/$id")

    private fun fullId(id: String): UiSelector = UiSelector().resourceId(id)

    private fun resourceShortName(@IdRes id: Int): String = context.resources.getResourceEntryName(id)

    private fun resourceName(@IdRes id: Int): String = context.resources.getResourceName(id)

    private fun resourceId(resourceName: String) =
            context.resources.getIdentifier(resourceName, "id", BuildConfig.APPLICATION_ID)

    /**
     *  Scroll to the bottom of the screen.
     *  NOTE: prefer the other scrolling helper methods are faster and/or more reliable than this.
     */
    private fun scrollDown(): Boolean {

        return try {
            UiScrollable(UiSelector().scrollable(true)).flingToEnd(3)
        } catch (e: UiObjectNotFoundException) {
            // if there's no scrollable item, don't scroll but let's continue if we can
            false
        }
    }

    /* Extension functions */

    fun String.toMoney(): Money =
        toMoney(locale)

    private fun String.toMoney(locale: Locale): Money {
        val t = this.split(" ")
        return Money.of(t[0].parseDecimal(locale), t[1])
    }

    fun String.toBtcMoney(): Money =
        toBtcMoney(locale)

    private fun String.toBtcMoney(locale: Locale): Money {
        return Money.of(this.parseDecimal(locale), "BTC")
    }

    fun MonetaryAmount.toShortText(mode: BitcoinUnit = BitcoinUnit.BTC): String {
        return MoneyHelper.formatShortMonetaryAmount(this, false, mode, locale)
    }

    /**
     * Parse string into a number.
     * Turns out it's not so easy to convert a string to a number when taking into account locale
     * specific formatting. Apparently, NumberFormat.parse(String) parse "1,23abc" as 1.23.
     * Based on https://stackoverflow.com/a/16879667/901465.
     */
    fun String.parseDecimal(): Double =
        parseDecimal(locale)

    /**
     * Parse string into a number.
     * Turns out it's not so easy to convert a string to a number when taking into account locale
     * specific formatting. Apparently, NumberFormat.parse(String) parse "1,23abc" as 1.23.
     * Based on https://stackoverflow.com/a/16879667/901465.
     */
    @kotlin.jvm.Throws(ParseException::class)
    private fun String.parseDecimal(locale: Locale): Double {
        val numberFormat: NumberFormat = NumberFormat.getNumberInstance(locale)
        val parsePosition = ParsePosition(0)

        val number: Number = numberFormat.parse(this, parsePosition)!!

        if (parsePosition.index != this.length) {
            throw ParseException("Invalid input", parsePosition.index)
        }

        return number.toDouble()
    }

    fun MonetaryAmount.toText(mode: BitcoinUnit = BitcoinUnit.BTC): String {
        return MoneyHelper.formatLongMonetaryAmount(this, mode, locale)
    }

    fun String.dropParenthesis() =
            drop(1).dropLast(1)

    fun String.dropUnit() =
            split(" ")[0]

    fun UiObject.child(@IdRes childResId: Int) =
            getChild(idSelector(childResId))

    fun UiObject.await() =
            await(2000)

    fun UiObject.await(millis: Long) =
            waitForExists(millis)

    fun UiObject.assertExists() {
        assertThat(this.waitForExists(3000)).isTrue()
    }

    fun UiObject.assertDoesntExist() {

        // Consider that obj may take some time to disappear. We loop until obj is gone or timeout
        val exitCondition = { !this.exists() }
        doUntil(3000, exitCondition) {
            // Do nothing
        }

        assertThat(this.exists()).isFalse()
    }

    fun UiObject.assertTextEquals(expectedText: String) {
        assertThat(this.text).isEqualTo(expectedText)
    }

    fun UiObject.assertEnabled() {
        assertThat(this.isEnabled).isTrue()
    }

    fun UiObject.assertDisabled() {
        assertThat(this.isEnabled).isFalse()
    }

    fun UiObject.assertEnabledAndClick(): Boolean {
        assertEnabled()
        return click()
    }

    // I WISH I could made these extension functions but we can't (as of this writing) static
    // static extension methods of JAVA classes (we can if the extended class is in Kotlin)
    fun Byid(@IdRes id: Int): BySelector =
            ByShortName(resourceShortName(id))

    fun ByShortName(resourceShortName: String) =
            By.res(BuildConfig.APPLICATION_ID, resourceShortName)

}