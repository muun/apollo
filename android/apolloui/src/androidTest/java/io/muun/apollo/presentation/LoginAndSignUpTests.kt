package io.muun.apollo.presentation


import androidx.test.espresso.Espresso
import io.muun.apollo.R
import io.muun.apollo.data.external.Gen
import io.muun.apollo.utils.RandomUser
import org.javamoney.moneta.Money
import org.junit.Test

open class LoginAndSignUpTests : BaseInstrumentationTest() {

    @Test
    fun test_01_a_new_user_can_sign_up() {
        autoFlows.signUp()
        autoFlows.deleteWallet()
    }

    @Test
    fun test_02_a_user_can_sign_in() {
        val user = RandomUser()

        autoFlows.createRecoverableUser(user.pin, user.email, user.password)
        autoFlows.logOut()

        autoFlows.signIn(user.email, user.password, pin = user.pin)

        autoFlows.logOut()
    }

    @Test
    fun test_03_error_messages_are_displayed_when_entering_wrong_credentials_on_sign_in() {
        val user = RandomUser()

        autoFlows.createRecoverableUser(user.pin, user.email, user.password)
        autoFlows.logOut()

        signInScreen.startLogin()

        // Reject invalid email:
        signInScreen.checkEmailConfirmEnabled(false)
        signInScreen.enterEmail("not a valid email")
        signInScreen.checkEmailConfirmEnabled(false)

        signInScreen.enterEmail("someNotRandomUnregisteredEmail@muun.com")
        signInScreen.checkEmailConfirmEnabled(true)
        signInScreen.confirmEmail()
        signInScreen.checkEmailError()

        signInScreen.enterEmail(user.email)
        signInScreen.checkEmailConfirmEnabled(true)
        signInScreen.confirmEmail()

        signInScreen.awaitEmailVerification(user.email)

        // Reject empty password:
        signInScreen.enterPassword("")
        signInScreen.checkPasswordConfirmEnabled(false)

        // Reject incorrect password:
        signInScreen.enterPassword("an incorrect password")
        signInScreen.confirmPassword()
        signInScreen.checkPasswordError()

        signInScreen.enterPassword(user.password)
        signInScreen.confirmPassword()

        autoFlows.setupPinAndReachHome(user.pin)

        autoFlows.logOut()
    }

    @Test
    fun test_04_back_navigation_works_for_sign_up_and_sign_in() {
        val user = RandomUser()
        val pin1 = Gen.randomPin()

        // Create Wallet (U.U)
        signInScreen.startSignup()

        label(R.string.choose_your_pin).assertExists()

        signInScreen.back()
        checkToastDisplayed(R.string.pin_error_on_setup_cancel)

        autoFlows.enterPin(pin1)            // choose pin
        device.pressBack()                  // oops, we want to choose another pin

        autoFlows.signUpFromPin(user.pin)
        autoFlows.setUpEmailAndPassword(user.email, user.password)
        autoFlows.setUpRecoveryCode()
        autoFlows.logOut()

        // Recover Wallet (U.U)

        // SignIn: Email Wait Verification
        signInScreen.startLogin()
        signInScreen.enterEmail(user.email)
        signInScreen.confirmEmail()

        signInScreen.checkEmailVerificationScreenDisplayed(user.email)
        signInScreen.back()                 // Back from Wait Email Verification
        signInScreen.back()

        // SignIn: Enter Password
        signInScreen.startLogin()
        signInScreen.enterEmail(user.email)
        signInScreen.confirmEmail()

        signInScreen.awaitEmailVerification(user.email)

        signInScreen.back()
        signInScreen.abortDialogCancel()
        signInScreen.back()
        signInScreen.abortDialogAbort()

        // SignIn: Enter RecoveryCode
        signInScreen.startLogin()
        signInScreen.enterEmail(user.email)
        signInScreen.confirmEmail()
        signInScreen.awaitEmailVerification(user.email)

        signInScreen.useRecoveryCode()

        // It appears we have some flakyness here. Sometimes we arrive at Enter RC screen with
        // input already focused which means the soft keyboard is active and the first back gets
        // "eaten" to dismiss the keyboard.
        id(R.id.signup_forgot_password_continue).assertExists()
        Espresso.closeSoftKeyboard()
        signInScreen.back()

        signInScreen.back()
        signInScreen.abortDialogCancel()
        signInScreen.back()
        signInScreen.abortDialogAbort()
    }

    @Test
    fun test_05_an_u_u_can_log_out_only_if_has_no_balance_and_no_pending_or_confirmed_txs() {
        // Note: U.U can log out/delete wallet is tested plenty as part of other tests as does
        // recoverable user logout.

        autoFlows.signUp()
        autoFlows.receiveMoneyFromNetwork(Money.of(0.02, "BTC"))

        // Case 1) U.u user with balance > 0 but unconfirmed receiving tx
        autoFlows.checkCannotDeleteWallet()

        // Case 2) U.u user with balance > 0 with confirmed receiving tx but not settled
        generateBlocksAndWaitForUpdate(1)
        autoFlows.checkCannotDeleteWallet()

        // Case 3) U.u user with balance > 0 with settled receiving tx
        generateBlocksAndWaitForUpdate(5) // Transaction Settled
        autoFlows.checkCannotDeleteWallet()

        // Case 4) U.u user with balance = 0 but unconfirmed spending tx

        autoFlows.spendAllFunds("some description")
        autoFlows.checkCannotDeleteWallet()

        // Case 5) U.u user with balance = 0 with confirmed spending tx but not settled

        generateBlocksAndWaitForUpdate(1)
        autoFlows.checkCannotDeleteWallet()

        // Case 6) U.u user with balance = 0 with settled spending tx

        generateBlocksAndWaitForUpdate(5) // Transaction Settled
        autoFlows.deleteWallet()

        // TODO make more convoluted scenarios? Failed txs, failed swaps, etc...?
    }
}