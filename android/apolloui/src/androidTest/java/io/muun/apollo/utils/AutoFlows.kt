package io.muun.apollo.utils

import android.content.Context
import android.os.SystemClock
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.domain.model.UserPhoneNumber
import io.muun.apollo.data.external.Gen
import io.muun.apollo.presentation.ui.debug.LappClient
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.common.model.DebtType
import io.muun.common.utils.LnInvoice
import org.javamoney.moneta.Money
import java.lang.IllegalStateException
import javax.money.MonetaryAmount

class AutoFlows(override val device: UiDevice,
                override val context: Context) : WithMuunInstrumentationHelpers {

    fun signUp(pin: List<Int> = Gen.pin()) {

        signInScreen.startSignup()

        signUpFromPin(pin)
    }

    fun signUpFromPin(pin: List<Int>) {
        setupPinAndReachHome(pin, true)

        // Dismiss Welcome Dialog
        dismissDialog()
    }

    fun setupPinAndReachHome(pin: List<Int>, isNewUser: Boolean = false) {
        enterPin(pin) // choose pin
        enterPin(pin) // confirm pin

        if (isNewUser) {
            val result = homeScreen.waitForWelcomeDialog()

            if (!result) {
                throw IllegalStateException("Did not arrive at home (with welcome dialog) on time.")
            }

        } else {
            homeScreen.waitUntilVisible()
        }
    }

    fun enterPin(pin: List<Int>) {
        pin.forEach { id("key_$it").click() }
    }

    fun createRecoverableUser(pin: List<Int>, email: String, password: String) {
        signUp(pin)
        setUpEmailAndPassword(email, password)
    }

    fun setUpEmailAndPassword(email: String, password: String) {
        homeScreen.goToSecurityCenter()

        securityCenterScreen.goToEmailAndPassword()

        emailPasswordScreen.startSetup()

        emailPasswordScreen.enterEmailAndConfirm(email)

        emailPasswordScreen.awaitEmailVerification(email)

        emailPasswordScreen.enterPasswordsAndConfirm(password)

        emailPasswordScreen.acceptConditions()

        emailPasswordScreen.finishSetup()

        // We're at the Security Center, let's go back to the home
        backToHome()
    }

    fun skipEmailAndPasswordSetup() {
        homeScreen.goToSecurityCenter()

        securityCenterScreen.goToEmailAndPassword()

        emailPasswordScreen.skipSetup()

        backToHome()
    }

    fun setUpRecoveryCode(): List<String> {
        homeScreen.goToSecurityCenter()

        securityCenterScreen.goToRecoveryCode()

        val recoveryCode = recoveryCodeScreen.fillForm()

        backToHome()

        return recoveryCode
    }

    fun exportEmergencyKit(sniffActivationCode: () -> String) {
        homeScreen.goToSecurityCenter()

        securityCenterScreen.goToEmergencyKit()

        emergencyKitSetupScreen.doCompleteFlow {
            sniffActivationCode()
        }

        backToHome()
    }

    fun signIn(email: String = Gen.email(),
               password: String? = null,
               recoveryCodeParts: List<String>? = null,
               pin: List<Int> = Gen.pin()) {

        signInScreen.fillSignInForm(email, password, recoveryCodeParts)

        setupPinAndReachHome(pin)
    }

    fun rcSignIn(recoveryCode: List<String>, email: String? = null, pin: List<Int> = Gen.pin()) {

        signInScreen.rcSignIn(recoveryCode, email)
        setupPinAndReachHome(pin)
    }

    fun logOut() {
        homeScreen.goToSettings()

        scrollTo {

            // Log out.
            settingsScreen.logout()

            // Confirm log out on pop-up message.
            normalizedLabel(R.string.settings_logout_alert_yes).click()
        }
    }

    /**
     * For Unrecoverable Users (some extra logic may apply, e.g u.u with positive balance may not
     * delete their wallets).
     */
    fun deleteWallet() {
        goToSettingsAndClickDeleteWallet()

        // Confirm on pop-up message.
        normalizedLabel(R.string.settings_delete_wallet_alert_yes).click()

        // Click Delete Success Continue Button
        normalizedLabel(R.string.delete_wallet_success_action).click()
    }

    /**
     * For Unrecoverable Users, in some cases, we won't let them delete wallet if they can lose
     * money. This AutoFlow is what happens in those scenarios.
     */
    fun checkCannotDeleteWallet() {
        goToSettingsAndClickDeleteWallet()

        normalizedLabel(R.string.settings_delete_wallet_explanation_title).waitForExists(5_000)
        normalizedLabel(R.string.settings_delete_wallet_explanation_action).click()

        backToHome()
    }

    fun receiveMoneyFromNetwork(amount: Money) {
        val expectedBalance = homeScreen.balanceInBtc
        val balanceAfter = expectedBalance.add(amount)

        // Generate an address:
        homeScreen.goToReceive()
        val address = receiveScreen.address

        // Hit RegTest to receive money from the network:
        LappClient().receiveBtc(amount.number.toDouble(), address)

        // If we return right now, we'll have to wait for the FCM notification to know we received
        // the money. If we give Syncer some time to see the transaction, the notification will be
        // waiting for us when we reach Home (and pullNotifications):
        SystemClock.sleep(1800)

        device.pressBack()

        // Wait for balance to be updated:
        homeScreen.waitUntilBalanceEquals(balanceAfter)
    }

    fun startOperationFromClipboardTo(addressOrInvoice: String) {
        Clipboard.write(addressOrInvoice)
        homeScreen.goToSend()
        id(R.id.uri_paster).click()
    }

    fun sendToAddressFromClipboard(money: Money, receivingAddress: String, description: String) {
        newOperation(money, description) {
            startOperationFromClipboardTo(receivingAddress)
        }
    }

    /**
     * AutoFlow to make an Operation using ALL FUNDS using the default way to reach new operation.
     * After submitting it, goes to OperationDetail screen to perform adequate checks. Leaves you at
     * home screen, with the operation PENDING.
     * <p>
     * To confirm or settle operation see settleOperation AutoFlow.
     */
    fun spendAllFunds(descriptionToEnter: String? = null) {

        val receivingAddress = "2N2y9wGHh7AfqwQ8dk5cQfhjvEAAq6xhjb6"

        spendAllFunds(descriptionToEnter) {
            startOperationFromClipboardTo(receivingAddress)
        }
    }

    /**
     * AutoFlow to make an Operation using ALL FUNDS. After submitting it, goes to OperationDetail
     * screen to perform adequate checks. Leaves you at home screen, with the operation PENDING.
     * <p>
     * To confirm or settle operation see settleOperation AutoFlow.
     */
    fun spendAllFunds(descriptionToEnter: String? = null,
                      reachNewOperation: () -> Unit) {

        val balanceBefore = homeScreen.balanceInBtc

        reachNewOperation()

        newOpScreen.waitUntilVisible()
        newOpScreen.fillFormUsingAllFunds(descriptionToEnter)

        // Ensure amounts in BTC (needed for post submit checks in balance and opDetail)
        if (!MoneyHelper.isBtc(newOpScreen.confirmedAmount.currency)) {
            newOpScreen.rotateAmountCurrencies()
        }

        // Keep these to check later:
        val amount = newOpScreen.confirmedAmount
        val fee = newOpScreen.confirmedFee
        val description = newOpScreen.confirmedDescription
        val total = newOpScreen.confirmedTotal
        newOpScreen.submit()

        homeScreen.checkBalanceCloseTo(balanceBefore.subtract(total))

        checkOperationDetails(amount, description, fee)
    }

    /**
     * AutoFlow to make an Operation. After submitting it, goes to OperationDetail screen to
     * perform adequate checks. Leaves you at home screen, with the operation PENDING.
     * <p>
     * To confirm or settle operation see settleOperation auto flow.
     */
    fun newOperation(amountToEnter: MonetaryAmount,
                     descriptionToEnter: String,
                     reachNewOperation: () -> Unit) {

        val balanceBefore = homeScreen.balanceInBtc

        reachNewOperation()

        newOpScreen.waitUntilVisible()
        newOpScreen.fillForm(amountToEnter, descriptionToEnter)

        // Keep these to check later:
        val amount = newOpScreen.confirmedAmount
        val fee = newOpScreen.confirmedFee
        val description = newOpScreen.confirmedDescription
        val total = newOpScreen.confirmedTotal
        newOpScreen.submit()

        homeScreen.checkBalanceCloseTo(balanceBefore.subtract(total))

        checkOperationDetails(amount, description, fee)
    }

    fun settleOperation(description: String) {

        // Settle the transaction:
        generateBlocksAndWaitForUpdate(6)

        homeScreen.goToOperationDetail(description) // will fail if still pending
        opDetailScreen.waitForStatusChange(context.getString(R.string.operation_completed));

        exitOpDetailAndReturnHome()
    }

    /**
     * AutoFlow to make a SubmarineSwap, using the default way to reach new operation and
     * performing default checks operationDetail screen. Leaves you at home screen.
     */
    fun newSubmarineSwap(amountInSat: Int, debtType: DebtType = DebtType.NONE) {

        val invoice = LappClient().getLnInvoice(amountInSat)
        val desc = "This is a note " + System.currentTimeMillis()

        newSubmarineSwap(invoice, desc, debtType) {
            startOperationFromClipboardTo(invoice.original)
        }

        checkSubmarineSwapSuccess()
    }

    /**
     * AutoFlow to make a SubmarineSwap. Leaves you at OperationDetail screen, so caller can perform
     * adequate checks.
     */
    fun newSubmarineSwap(invoice: LnInvoice,
                         descriptionToEnter: String,
                         debtType: DebtType = DebtType.NONE,
                         reachNewOperation: () -> Unit) {

        val balanceBefore = homeScreen.balanceInBtc

        reachNewOperation()

        newOpScreen.waitUntilVisible()
        newOpScreen.fillForm(invoice, descriptionToEnter, debtType)

        // Keep these to check later:
        val amount = newOpScreen.confirmedAmount
        val description = newOpScreen.confirmedDescription
        val lightningFee =  newOpScreen.confirmedFee
        val total = newOpScreen.confirmedTotal

        newOpScreen.submit()

        homeScreen.checkBalanceCloseTo(balanceBefore.subtract(total))

        // Account for the special case where swap is completed REALLY fast

        homeScreen.goToOperationDetailContaining(description) // don't care about status

        opDetailScreen.checkStatus(
            context.getString(R.string.operation_pending),
            context.getString(R.string.operation_completed)
        )

        opDetailScreen.checkAmount(amount)
        opDetailScreen.checkDescription(description)

        lightningFee?.let {
            opDetailScreen.checkLightningFee(it)
        }

        opDetailScreen.checkInvoice(invoice.original)
    }

    /**
     * Perform submarine swap checks in success scenarios. By default, assumes we are in
     * Operation Detail screen.
     */
    fun checkSubmarineSwapSuccess(is0Conf: Boolean = true, reachOperationDetail: () -> Unit = {}) {

        reachOperationDetail()

        if (is0Conf) {

            checkSwapConfirmed()

        } else {
            // Confirm the swap:
            LappClient().generateBlocks(1)
            checkSwapConfirmed()

        }

        exitOpDetailAndReturnHome()
    }

    fun checkSubmarineSwapFail(reachOperationDetail: () -> Unit = {}) {
        reachOperationDetail()
        opDetailScreen.waitForStatusChange(R.string.operation_failed)
        // TODO more?

        exitOpDetailAndReturnHome()
    }

    private fun checkSwapConfirmed() {
        opDetailScreen.waitForStatusChange(R.string.operation_completed)
        // TODO check receving node?
    }

    fun tryAllFeesAndExit() {

        // Try medium fee:
        newOpScreen.goToEditFee()
        val optionMedium = recomFeeScreen.selectFeeOptionMedium()
        recomFeeScreen.confirmFee()

        newOpScreen.checkConfirmedData(fee=optionMedium.secondaryAmount)

        // Try slow fee (TODO we should control whether it appears, instead of asking):
        newOpScreen.goToEditFee()
        if (recomFeeScreen.hasFeeOptionSlow()) {
            val optionSlow = recomFeeScreen.selectFeeOptionSlow()
            recomFeeScreen.confirmFee()

            newOpScreen.checkConfirmedData(fee=optionSlow.secondaryAmount)
        } else {
            device.pressBack()
        }

        // Try fast fee:
        newOpScreen.goToEditFee()
        val optionFast = recomFeeScreen.selectFeeOptionFast()
        recomFeeScreen.confirmFee()

        newOpScreen.checkConfirmedData(fee=optionFast.secondaryAmount)

        // Try manual fee:
        newOpScreen.goToEditFee()
        recomFeeScreen.goToManualFee()
        val optionManual = manualFeeScreen.editFeeRate(optionMedium.feeRate + 1)
        manualFeeScreen.confirmFeeRate()

        // TODO test mempool congested?

        newOpScreen.checkConfirmedData(fee=optionManual.secondaryAmount)

        // Abort operation like a boss:
        device.pressBack()
        device.pressBack()
        device.pressBack()
    }

    fun changePassword(user: RandomUser, newPassword: String) {

        homeScreen.goToSettings()
        settingsScreen.goToChangePassword()

        changePasswordScreen.fillForm(user, newPassword)

        backToHome()
    }

    private fun dismissDialog() {
        device.pressBack() // Pressing back should dismiss dialog ;)
    }

    // PRIVATE, helper stuff

    private fun checkOperationDetails(amount: Money, description: String, fee: Money) {
        homeScreen.goToOperationDetail(description, isPending = true)

        opDetailScreen.checkStatus(context.getString(R.string.operation_pending))
        opDetailScreen.checkAmount(amount)
        opDetailScreen.checkFee(fee)
        opDetailScreen.checkDescription(description)

        exitOpDetailAndReturnHome()
    }

    private fun goToSettingsAndClickDeleteWallet() {
        homeScreen.goToSettings()

        scrollTo {
            settingsScreen.deleteWallet()
        }
    }

    private fun exitOpDetailAndReturnHome() {
        device.pressBack()

        checkScreenShows(R.string.home_operations_list_title)

        device.pressBack()
    }

    private fun backToHome() {
        id(R.id.home_fragment).click()
    }
}
